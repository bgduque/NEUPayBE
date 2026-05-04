# NEU Payment — AWS Deployment Guide

Step-by-step guide to deploy the Spring Boot backend (`neupayment.jar`) into
AWS so it can talk to your existing **Aurora / RDS PostgreSQL** cluster and
serve the NeuPay iOS app.

This guide covers two paths:

- **Path A — Elastic Beanstalk (Docker single-container).** Simplest. EB
  manages an ALB, an Auto Scaling group, and the EC2 instance(s) for you.
- **Path B — ECS on Fargate behind an ALB.** Recommended for production:
  no servers to patch, native integration with Secrets Manager, easy blue/
  green deploys.

If you've never deployed to AWS before, start with **Path A**, get end-to-end
working, then migrate to **Path B** at your own pace.

---

## 0. Architecture overview

```
┌──────────────────────────┐
│ NeuPay iOS app           │
└─────────────┬────────────┘
              │ HTTPS (TLS 1.2+)
              ▼
┌──────────────────────────┐
│ AWS WAF (optional)       │   ← rate-limit, geo-block, bad-bot rules
└─────────────┬────────────┘
              ▼
┌──────────────────────────┐
│ Application Load Balancer│   ← ACM-issued cert, HTTPS:443 → HTTP:8080
└─────────────┬────────────┘
              ▼
┌──────────────────────────┐
│ neu-payment service      │
│  Spring Boot (Java 21)   │   ← reads secrets from Secrets Manager
│  EB or ECS Fargate task  │   ← writes to CloudWatch Logs
└─────┬─────────┬──────────┘
      │         │
      │         └─────────────────────────┐
      ▼                                   ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│ Aurora / RDS PostgreSQL  │  │ Secrets Manager          │
│ (your existing cluster)  │  │ neu/payment/jwt-secret   │
│ port 5432, private subnet│  │ neu/payment/qr-hmac      │
└──────────────────────────┘  │ neu/payment/db-creds     │
                              └──────────────────────────┘
```

Region used throughout: **`ap-southeast-1` (Singapore)** — closest AWS region
to Manila. Substitute your own region everywhere if different.

---

## 1. Prerequisites

| Tool                         | Why                                       |
|------------------------------|-------------------------------------------|
| AWS account + IAM admin      | Provision resources                       |
| AWS CLI v2 (`aws --version`) | Run the commands below                    |
| Docker                       | Build the container image                 |
| `eb` CLI (Path A only)       | `pip install awsebcli`                    |
| Aurora / RDS cluster         | Already created — note its endpoint + creds |
| ACM certificate              | TLS for your domain (`api.neu.edu.ph`)    |
| The repo built once          | `mvn -DskipTests package` succeeds locally|

Set these shell vars; the snippets below reuse them:

```bash
export AWS_REGION=ap-southeast-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export APP_NAME=neu-payment
```

---

## 2. Lock down the database

### 2.1 Create a schema-scoped DB user

Don't reuse the Aurora master credentials at runtime. Create an app user with
just the privileges Flyway and the application need:

```sql
-- Connect to Aurora as the master user (psql or AWS Query Editor):
CREATE DATABASE neupayment;
\c neupayment
CREATE USER neupayment_app WITH PASSWORD 'CHANGE-ME-strong-password';
GRANT CONNECT ON DATABASE neupayment TO neupayment_app;
GRANT USAGE, CREATE ON SCHEMA public TO neupayment_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO neupayment_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO neupayment_app;
-- pgcrypto extension is required by V1__init.sql:
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

### 2.2 Restrict the DB security group

The Aurora cluster's security group should accept inbound on port `5432` only
from the security group that runs the Spring Boot service (created later in
**§4** or **§5**). Never expose `5432` to `0.0.0.0/0`.

### 2.3 Enable backups

Aurora has automatic backups by default (7 days). For a finance app, also
enable a cross-region copy of your snapshots and consider a **15-minute**
PITR retention (Console → RDS → Cluster → Modify).

---

## 3. Store secrets in Secrets Manager

The application expects three secrets at runtime: the JWT signing key, the QR
HMAC key, and the DB password. **Never put these in env vars baked into the
image.** Use Secrets Manager and pull at task / instance startup.

```bash
# 1. Generate fresh secrets (locally, just once):
JWT=$(openssl rand -base64 96)
QR=$(openssl rand -base64 48)

# 2. Create the secrets:
aws secretsmanager create-secret \
    --name neu/payment/jwt-secret \
    --secret-string "$JWT" --region "$AWS_REGION"

aws secretsmanager create-secret \
    --name neu/payment/qr-hmac \
    --secret-string "$QR"  --region "$AWS_REGION"

aws secretsmanager create-secret \
    --name neu/payment/db-creds \
    --secret-string '{"username":"neupayment_app","password":"CHANGE-ME-strong-password"}' \
    --region "$AWS_REGION"
```

Take note of the resulting **secret ARNs** — you'll reference them in the IAM
policy and the task / EB environment.

> Tip: store the bootstrap admin password (`NEU_BOOTSTRAP_ADMIN_PASSWORD`) the
> same way, but only attach it to the environment for the **first deploy**.
> Remove it after the admin signs in once.

---

## 4. Build & push the Docker image

The repo already ships a multi-stage `Dockerfile` based on Azul Zulu 21.

```bash
cd /Users/lazarus/Documents/neupaymentbe

# 4.1 Create the ECR repo (idempotent)
aws ecr describe-repositories --repository-names $APP_NAME --region $AWS_REGION 2>/dev/null \
  || aws ecr create-repository --repository-name $APP_NAME \
       --image-scanning-configuration scanOnPush=true \
       --region $AWS_REGION

# 4.2 Authenticate Docker with ECR
aws ecr get-login-password --region $AWS_REGION \
  | docker login --username AWS --password-stdin \
        $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# 4.3 Build, tag, push
docker build --platform linux/amd64 -t $APP_NAME:1.0.0 .

ECR_URI=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$APP_NAME
docker tag $APP_NAME:1.0.0   $ECR_URI:1.0.0
docker tag $APP_NAME:1.0.0   $ECR_URI:latest
docker push $ECR_URI:1.0.0
docker push $ECR_URI:latest
```

> **Why `--platform linux/amd64`?** If you're building on Apple Silicon, the
> default image arch is `arm64`. EB and most Fargate task definitions run on
> `x86_64`. Match the runtime arch (or pick `arm64` Fargate tasks deliberately
> for cheaper compute — both arches work fine).

---

## Path A — Elastic Beanstalk (Docker single-container)

Easiest path. Skip to **Path B** if you already have ECS or want zero-server.

### A1. Initialise the EB application

```bash
eb init $APP_NAME \
    --region $AWS_REGION \
    --platform "Docker" \
    --keyname <ec2-keypair-name>     # optional, for SSH access
```

### A2. Wire the runtime to the ECR image

Edit `deploy/Dockerrun.aws.json` (already in the repo) and replace
`<your-account>` with `$AWS_ACCOUNT_ID`:

```json
{
  "AWSEBDockerrunVersion": "1",
  "Image": {
    "Name": "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/neu-payment:latest",
    "Update": "true"
  },
  "Ports": [
    { "ContainerPort": 8080, "HostPort": 80 }
  ],
  "Logging": "/var/log/nginx"
}
```

EB also automatically picks up `deploy/.ebextensions/01-nginx.config` which
enables HSTS, secure headers, and a 1 MB request body cap.

### A3. Create the EB environment

```bash
eb create $APP_NAME-prod \
    --region $AWS_REGION \
    --instance-type t3.small \
    --vpc.id  <vpc-id> \
    --vpc.ec2subnets  <private-subnet-1>,<private-subnet-2> \
    --vpc.elbsubnets  <public-subnet-1>,<public-subnet-2> \
    --vpc.elbpublic \
    --vpc.publicip \
    --tier WebServer \
    --elb-type application
```

### A4. Set environment variables

Set these from the AWS Console (EB → Configuration → Software → Environment
properties) **or** from the CLI in one shot:

| Variable                          | Value                                                                                  |
|-----------------------------------|----------------------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`          | `prod`                                                                                 |
| `DB_HOST`                         | your Aurora writer endpoint                                                            |
| `DB_PORT`                         | `5432`                                                                                 |
| `DB_NAME`                         | `neupayment`                                                                           |
| `DB_USER`                         | `{{resolve:secretsmanager:neu/payment/db-creds:SecretString:username}}`               |
| `DB_PASSWORD`                     | `{{resolve:secretsmanager:neu/payment/db-creds:SecretString:password}}`               |
| `NEU_JWT_SECRET`                  | `{{resolve:secretsmanager:neu/payment/jwt-secret}}`                                   |
| `NEU_QR_HMAC_SECRET`              | `{{resolve:secretsmanager:neu/payment/qr-hmac}}`                                      |
| `NEU_CORS_ALLOWED_ORIGINS`        | `https://neu.edu.ph` (and your iOS deep-link origin if any)                            |
| `NEU_BOOTSTRAP_ADMIN_EMAIL`       | `admin@neu.edu.ph` *(remove after first deploy)*                                       |
| `NEU_BOOTSTRAP_ADMIN_PASSWORD`    | strong password *(remove after first deploy)*                                          |
| `NEU_BOOTSTRAP_ADMIN_NAME`        | `NEU System Admin` *(remove after first deploy)*                                       |

The `{{resolve:secretsmanager:...}}` syntax is an EB feature that pulls the
value at instance startup — the secret never appears in the configuration.

```bash
eb setenv \
  SPRING_PROFILES_ACTIVE=prod \
  DB_HOST=neupayment-aurora.cluster-xxxxxx.ap-southeast-1.rds.amazonaws.com \
  DB_PORT=5432 \
  DB_NAME=neupayment \
  "DB_USER={{resolve:secretsmanager:neu/payment/db-creds:SecretString:username}}" \
  "DB_PASSWORD={{resolve:secretsmanager:neu/payment/db-creds:SecretString:password}}" \
  "NEU_JWT_SECRET={{resolve:secretsmanager:neu/payment/jwt-secret}}" \
  "NEU_QR_HMAC_SECRET={{resolve:secretsmanager:neu/payment/qr-hmac}}" \
  NEU_CORS_ALLOWED_ORIGINS=https://neu.edu.ph
```

### A5. Grant the EB instance role access to Secrets Manager

The EB instance profile is named `aws-elasticbeanstalk-ec2-role` by default.
Attach a policy that allows reading just the three secrets:

```bash
cat > /tmp/secrets-read.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["secretsmanager:GetSecretValue"],
    "Resource": [
      "arn:aws:secretsmanager:$AWS_REGION:$AWS_ACCOUNT_ID:secret:neu/payment/jwt-secret-*",
      "arn:aws:secretsmanager:$AWS_REGION:$AWS_ACCOUNT_ID:secret:neu/payment/qr-hmac-*",
      "arn:aws:secretsmanager:$AWS_REGION:$AWS_ACCOUNT_ID:secret:neu/payment/db-creds-*"
    ]
  }]
}
EOF

aws iam put-role-policy \
    --role-name aws-elasticbeanstalk-ec2-role \
    --policy-name neu-payment-secrets-read \
    --policy-document file:///tmp/secrets-read.json
```

### A6. Deploy

```bash
eb deploy $APP_NAME-prod
```

Watch the rollout in the EB Console. Health probe is `/actuator/health/liveness`
(already wired in the Dockerfile `HEALTHCHECK`). On first successful boot, the
`BootstrapAdminRunner` provisions the ADMIN user and Flyway runs migrations
`V1__init.sql` and `V2__seed_cash_in_locations.sql` against Aurora.

### A7. Add the TLS listener

```bash
eb config $APP_NAME-prod    # opens an editor; under aws:elbv2:listener:443
                            # set Protocol=HTTPS and SSLCertificateArns=<your ACM ARN>
                            # also set aws:elbv2:listener:default Protocol=HTTPS
                            # to redirect HTTP→HTTPS
```

After save, EB rolls the change. The ALB DNS will look like
`neu-payment-prod.eba-xxx.ap-southeast-1.elasticbeanstalk.com`.

### A8. Map your domain

Create a Route 53 ALIAS record `api.neu.edu.ph → <EB ALB>`. Verify:

```bash
curl -sS https://api.neu.edu.ph/actuator/health
# {"status":"UP"}
```

---

## Path B — ECS on Fargate (production-grade)

Use this when you outgrow EB or want zero-server operations. The
neupayment image is the same; the difference is how it's run.

### B1. Cluster + log group

```bash
aws ecs create-cluster --cluster-name neu-payment --region $AWS_REGION

aws logs create-log-group --log-group-name /ecs/neu-payment --region $AWS_REGION
aws logs put-retention-policy --log-group-name /ecs/neu-payment \
    --retention-in-days 30 --region $AWS_REGION
```

### B2. IAM roles (task execution + task role)

The **execution role** lets ECS pull from ECR and write logs. The **task
role** is what the application code uses (Secrets Manager reads).

```bash
# Execution role — trust ECS tasks to assume it
cat > /tmp/ecs-trust.json <<EOF
{ "Version":"2012-10-17", "Statement":[{
    "Effect":"Allow",
    "Principal":{"Service":"ecs-tasks.amazonaws.com"},
    "Action":"sts:AssumeRole"
}]}
EOF

aws iam create-role --role-name neu-payment-exec \
    --assume-role-policy-document file:///tmp/ecs-trust.json
aws iam attach-role-policy --role-name neu-payment-exec \
    --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

# Task role — same trust, plus secrets-read
aws iam create-role --role-name neu-payment-task \
    --assume-role-policy-document file:///tmp/ecs-trust.json
aws iam put-role-policy --role-name neu-payment-task \
    --policy-name neu-payment-secrets-read \
    --policy-document file:///tmp/secrets-read.json
```

### B3. Task definition

Create `deploy/ecs-task-definition.json`:

```json
{
  "family": "neu-payment",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::ACCOUNT_ID:role/neu-payment-exec",
  "taskRoleArn":      "arn:aws:iam::ACCOUNT_ID:role/neu-payment-task",
  "runtimePlatform": { "cpuArchitecture": "X86_64", "operatingSystemFamily": "LINUX" },
  "containerDefinitions": [{
    "name": "neu-payment",
    "image": "ACCOUNT_ID.dkr.ecr.ap-southeast-1.amazonaws.com/neu-payment:1.0.0",
    "portMappings": [{ "containerPort": 8080, "protocol": "tcp" }],
    "essential": true,
    "environment": [
      { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" },
      { "name": "DB_HOST", "value": "neupayment-aurora.cluster-xxxxxx.ap-southeast-1.rds.amazonaws.com" },
      { "name": "DB_PORT", "value": "5432" },
      { "name": "DB_NAME", "value": "neupayment" },
      { "name": "NEU_CORS_ALLOWED_ORIGINS", "value": "https://neu.edu.ph" }
    ],
    "secrets": [
      { "name": "DB_USER",            "valueFrom": "arn:aws:secretsmanager:ap-southeast-1:ACCOUNT_ID:secret:neu/payment/db-creds:username::" },
      { "name": "DB_PASSWORD",        "valueFrom": "arn:aws:secretsmanager:ap-southeast-1:ACCOUNT_ID:secret:neu/payment/db-creds:password::" },
      { "name": "NEU_JWT_SECRET",     "valueFrom": "arn:aws:secretsmanager:ap-southeast-1:ACCOUNT_ID:secret:neu/payment/jwt-secret" },
      { "name": "NEU_QR_HMAC_SECRET", "valueFrom": "arn:aws:secretsmanager:ap-southeast-1:ACCOUNT_ID:secret:neu/payment/qr-hmac" }
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/neu-payment",
        "awslogs-region": "ap-southeast-1",
        "awslogs-stream-prefix": "neu-payment"
      }
    },
    "healthCheck": {
      "command": ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1"],
      "interval": 30, "timeout": 5, "retries": 3, "startPeriod": 60
    }
  }]
}
```

> Replace every `ACCOUNT_ID` placeholder. The trailing `:username::` /
> `:password::` notation lets ECS pull individual JSON keys from a
> single Secrets Manager entry.

Register it:

```bash
aws ecs register-task-definition \
    --cli-input-json file://deploy/ecs-task-definition.json \
    --region $AWS_REGION
```

### B4. Application Load Balancer

```bash
# Security group for the ALB (open 443 to the world)
aws ec2 create-security-group --group-name neu-payment-alb --vpc-id <vpc-id> \
    --description "ALB SG for neu-payment" --region $AWS_REGION
aws ec2 authorize-security-group-ingress --group-id <alb-sg> \
    --protocol tcp --port 443 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id <alb-sg> \
    --protocol tcp --port 80  --cidr 0.0.0.0/0   # for HTTP→HTTPS redirect

# Security group for the tasks (only ALB → task on 8080)
aws ec2 create-security-group --group-name neu-payment-tasks --vpc-id <vpc-id> \
    --description "Fargate tasks SG"
aws ec2 authorize-security-group-ingress --group-id <task-sg> \
    --protocol tcp --port 8080 --source-group <alb-sg>

# ALB
aws elbv2 create-load-balancer --name neu-payment-alb \
    --subnets <public-subnet-1> <public-subnet-2> \
    --security-groups <alb-sg> --type application

# Target group (IP target type for Fargate)
aws elbv2 create-target-group --name neu-payment-tg \
    --protocol HTTP --port 8080 --vpc-id <vpc-id> --target-type ip \
    --health-check-path /actuator/health/readiness \
    --health-check-interval-seconds 15 \
    --healthy-threshold-count 2 --unhealthy-threshold-count 3

# HTTPS listener (uses your ACM cert)
aws elbv2 create-listener --load-balancer-arn <alb-arn> \
    --protocol HTTPS --port 443 \
    --certificates CertificateArn=<acm-arn> \
    --ssl-policy ELBSecurityPolicy-TLS13-1-2-2021-06 \
    --default-actions Type=forward,TargetGroupArn=<tg-arn>

# HTTP listener that redirects to HTTPS
aws elbv2 create-listener --load-balancer-arn <alb-arn> \
    --protocol HTTP --port 80 \
    --default-actions \
      'Type=redirect,RedirectConfig={Protocol=HTTPS,Port=443,StatusCode=HTTP_301}'
```

Allow the ALB SG inbound on the **Aurora SG** on port 5432 — that's how the
tasks reach the database.

### B5. ECS service

```bash
aws ecs create-service \
    --cluster neu-payment \
    --service-name neu-payment-svc \
    --task-definition neu-payment \
    --desired-count 2 \
    --launch-type FARGATE \
    --platform-version LATEST \
    --network-configuration "awsvpcConfiguration={subnets=[<private-subnet-1>,<private-subnet-2>],securityGroups=[<task-sg>],assignPublicIp=DISABLED}" \
    --load-balancers "targetGroupArn=<tg-arn>,containerName=neu-payment,containerPort=8080" \
    --health-check-grace-period-seconds 90 \
    --deployment-configuration "deploymentCircuitBreaker={enable=true,rollback=true},minimumHealthyPercent=100,maximumPercent=200" \
    --region $AWS_REGION
```

`deploymentCircuitBreaker` automatically rolls back a bad deploy. The service
runs in private subnets and pulls images / secrets through your VPC's NAT or
VPC endpoints.

### B6. Auto-scaling

```bash
aws application-autoscaling register-scalable-target \
    --service-namespace ecs \
    --resource-id service/neu-payment/neu-payment-svc \
    --scalable-dimension ecs:service:DesiredCount \
    --min-capacity 2 --max-capacity 10

aws application-autoscaling put-scaling-policy \
    --service-namespace ecs \
    --resource-id service/neu-payment/neu-payment-svc \
    --scalable-dimension ecs:service:DesiredCount \
    --policy-name cpu70 \
    --policy-type TargetTrackingScaling \
    --target-tracking-scaling-policy-configuration \
'{ "TargetValue": 70.0,
   "PredefinedMetricSpecification": { "PredefinedMetricType": "ECSServiceAverageCPUUtilization" },
   "ScaleInCooldown": 300, "ScaleOutCooldown": 60 }'
```

---

## 5. WAF (recommended for finance apps)

```bash
aws wafv2 create-web-acl \
    --name neu-payment-waf --scope REGIONAL \
    --default-action Allow={} \
    --visibility-config SampledRequestsEnabled=true,CloudWatchMetricsEnabled=true,MetricName=neu-payment \
    --rules '[
      { "Name":"AWSManagedCommonRuleSet","Priority":0,
        "Statement":{"ManagedRuleGroupStatement":{"VendorName":"AWS","Name":"AWSManagedRulesCommonRuleSet"}},
        "OverrideAction":{"None":{}},
        "VisibilityConfig":{"SampledRequestsEnabled":true,"CloudWatchMetricsEnabled":true,"MetricName":"common"} },
      { "Name":"RateLimit","Priority":1,
        "Statement":{"RateBasedStatement":{"Limit":2000,"AggregateKeyType":"IP"}},
        "Action":{"Block":{}},
        "VisibilityConfig":{"SampledRequestsEnabled":true,"CloudWatchMetricsEnabled":true,"MetricName":"rate"} }
    ]'

aws wafv2 associate-web-acl --web-acl-arn <web-acl-arn> --resource-arn <alb-arn>
```

The rate limit is 2000 req / 5min / IP — tune for your campus traffic.

---

## 6. CloudWatch logs, metrics, alarms

`logConfiguration` in the task def already ships stdout to
`/ecs/neu-payment`. Spring Boot logs in single-line format in `prod`. Useful
queries (CloudWatch Logs Insights):

```
fields @timestamp, @message
| filter @message like /WALLET_CHARGE|WALLET_CREDIT/
| sort @timestamp desc
```

Set alarms:

| Alarm                          | Trigger                                      |
|--------------------------------|----------------------------------------------|
| ALB 5xx > 1% over 5 min        | service is failing                           |
| Target Group HealthyHostCount = 0 | all tasks unhealthy                       |
| RDS CPU > 80% for 10 min       | tune queries / scale instance               |
| RDS FreeStorageSpace < 20%     | grow Aurora storage                         |
| Secrets Manager rotation lag   | rotate keys quarterly                        |

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name neu-payment-5xx \
  --metric-name HTTPCode_Target_5XX_Count --namespace AWS/ApplicationELB \
  --dimensions Name=LoadBalancer,Value=<alb-suffix> Name=TargetGroup,Value=<tg-suffix> \
  --statistic Sum --period 60 --threshold 5 \
  --evaluation-periods 5 --comparison-operator GreaterThanThreshold \
  --alarm-actions <sns-topic-arn>
```

---

## 7. First-time bootstrap

After the service is healthy:

1. The `BootstrapAdminRunner` (see `application.yml → neu.bootstrap`) created
   the first ADMIN if you set `NEU_BOOTSTRAP_ADMIN_EMAIL` /
   `NEU_BOOTSTRAP_ADMIN_PASSWORD` in §A4 / §B3.
2. Sign in as that admin via `POST /api/v1/auth/login`.
3. Use `POST /api/v1/admin/staff` to provision additional cashiers / admins.
4. **Remove the bootstrap env vars** from EB / the ECS task definition and
   redeploy:
   ```bash
   eb setenv NEU_BOOTSTRAP_ADMIN_EMAIL= NEU_BOOTSTRAP_ADMIN_PASSWORD=
   ```
   (or update the task definition and `aws ecs update-service`.)

---

## 8. Wire the iOS app

In `neupayment/Components/Components.swift` (or wherever `NEUAPIConfig` lives),
replace the placeholder base URL with your domain:

```swift
enum NEUAPIConfig {
    static let baseURL = "https://api.neu.edu.ph"
}
```

The iOS app already uses bearer-token auth (`Authorization: Bearer <jwt>`) and
sends `X-Device-Id` for refresh-token / biometric flows. No further wire-up
needed.

---

## 9. Day-2 operations

### Rotate secrets

JWT and QR HMAC keys should rotate quarterly:

```bash
NEW_JWT=$(openssl rand -base64 96)
aws secretsmanager put-secret-value \
    --secret-id neu/payment/jwt-secret \
    --secret-string "$NEW_JWT" --region $AWS_REGION

# Force a fresh deploy so tasks pick up the new value:
aws ecs update-service --cluster neu-payment --service neu-payment-svc \
    --force-new-deployment --region $AWS_REGION
```

Rotating the JWT secret invalidates all outstanding access tokens — that's
deliberate; clients refresh transparently. Refresh tokens are stored hashed
and survive rotation.

### Push a new version

```bash
docker build --platform linux/amd64 -t $APP_NAME:1.0.1 .
docker tag $APP_NAME:1.0.1 $ECR_URI:1.0.1
docker push $ECR_URI:1.0.1

# Path A
eb deploy $APP_NAME-prod

# Path B — register a new task def revision pinned to :1.0.1 then update-service
```

### Roll back

```bash
# Path A
eb history                                 # find previous version label
eb deploy --version <previous-label>

# Path B
aws ecs update-service \
    --service neu-payment-svc --cluster neu-payment \
    --task-definition neu-payment:<previous-revision>
```

### Database migrations

Flyway runs every startup. New migrations belong in
`src/main/resources/db/migration/V3__...sql`, `V4__...sql`, …. Any migration
that isn't backwards-compatible should be split into expand → contract phases
across two deploys.

---

## 10. Troubleshooting

| Symptom                                              | Likely cause                                                     |
|------------------------------------------------------|------------------------------------------------------------------|
| `JWT secret too short` on startup                    | `NEU_JWT_SECRET` < 64 raw bytes. Regenerate with `openssl rand -base64 96`. |
| Tasks fail health check, ALB shows 502               | Container can't reach Aurora — check task SG → Aurora SG rule.    |
| `flyway:Migration ... failed`                        | The DB user lacks `CREATE` on schema. Re-run grants in §2.1.      |
| `pgcrypto` not installed                             | Run `CREATE EXTENSION IF NOT EXISTS pgcrypto` as Aurora master.   |
| Cold start latency 5–10s                             | Spring Boot boot time. Enable provisioned capacity / desired-count ≥ 2. |
| `OptimisticLockingFailureException` on top-up        | Two cashiers credited the same wallet simultaneously — client should retry; the @Idempotent header makes retries safe. |
| `ECS unable to assume role`                          | The `executionRoleArn` is missing or the trust policy is wrong.   |
| `Secrets Manager: AccessDeniedException`             | Task role lacks `secretsmanager:GetSecretValue` on the ARN.       |

Log into a running task for live debugging (Path B):

```bash
aws ecs execute-command --cluster neu-payment \
    --task <task-id> --container neu-payment --interactive --command "/bin/sh"
```

(Requires `enableExecuteCommand=true` on the service and the task role to
have `ssmmessages:*` permissions.)

---

## 11. Cost ballpark (ap-southeast-1, 24/7)

| Component                                    | Monthly USD (approx) |
|----------------------------------------------|----------------------|
| Aurora PostgreSQL `db.t4g.medium` × 1 + 50GB | ~$80                 |
| ECS Fargate 1 vCPU / 2 GB × 2 tasks          | ~$56                 |
| ALB                                           | ~$22                 |
| NAT Gateway (1 AZ)                            | ~$32 + $0.045/GB     |
| CloudWatch Logs (10 GB / month)               | ~$5                  |
| Secrets Manager (3 secrets)                  | ~$1.20               |
| **Total**                                     | **~$200**            |

Switch to **Path A (single t3.small + EB managed ALB)** for ~$50–70/mo if
you're not yet at production scale. Aurora dominates the bill.

---

## 12. Quick reference — what the application reads

The app reads exactly these env vars:

| Var                              | Required | Notes                                                        |
|----------------------------------|----------|--------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`         | yes      | `prod` in AWS                                                |
| `DB_HOST`, `DB_PORT`, `DB_NAME`  | yes      | Aurora endpoint and DB                                       |
| `DB_USER`, `DB_PASSWORD`         | yes      | from Secrets Manager                                         |
| `NEU_JWT_SECRET`                 | yes      | base64, ≥64 raw bytes                                        |
| `NEU_QR_HMAC_SECRET`             | yes      | base64, ≥32 raw bytes                                        |
| `NEU_CORS_ALLOWED_ORIGINS`       | yes      | comma-separated list                                         |
| `NEU_BOOTSTRAP_ADMIN_*`          | first deploy only | provisions the first ADMIN; remove afterwards       |
| `PORT`                           | optional | defaults to 8080                                             |
| `JAVA_OPTS`                      | optional | the Dockerfile sets sane defaults                            |
| `DB_POOL_MAX`                    | optional | Hikari max pool size; default 20 in `prod`                   |

Any other variable in `application.yml` can also be overridden via env (Spring
Boot relaxed binding — `neu.security.jwt.access-token-ttl` becomes
`NEU_SECURITY_JWT_ACCESS_TOKEN_TTL=PT30M` if you ever need to extend it).

---

## 13. Hand-off checklist

Before you close the laptop:

- [ ] DB user `neupayment_app` created and `pgcrypto` extension enabled
- [ ] Three secrets exist in Secrets Manager (`jwt-secret`, `qr-hmac`, `db-creds`)
- [ ] ECR repo `neu-payment` populated with `1.0.0` and `latest` tags
- [ ] EB or ECS service is healthy (ALB target group ✅)
- [ ] HTTPS works: `curl https://api.neu.edu.ph/actuator/health` → `UP`
- [ ] Bootstrap admin signed in, created at least one cashier, then bootstrap env vars removed
- [ ] iOS `NEUAPIConfig.baseURL` updated and signed builds tested against prod
- [ ] CloudWatch alarms created and routed to an SNS topic / email
- [ ] WAF associated with the ALB
- [ ] Aurora automatic backups + cross-region snapshot configured
- [ ] Secrets rotation calendar entry set (quarterly)

Done. Hand the URL to the iOS team and the admin credentials (sealed) to the
cashier office.
