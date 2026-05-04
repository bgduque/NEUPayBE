# NEU Payment Backend

Spring Boot 3 backend for the NeuPay iOS app. Persistent storage on PostgreSQL
(Aurora / RDS), JWT auth with biometric (Face ID) step-up, real rotating QR
tokens, and a cashier-facing admin panel.

## Tech

- Spring Boot 3.3 (Java 21, Azul Zulu JDK)
- Spring Data JPA + Hibernate
- Spring Security (JWT) + Method-level `@PreAuthorize`
- PostgreSQL (Aurora / RDS) + Flyway migrations
- ZXing for real QR PNG generation
- BCrypt 12-round password hashing
- Optimistic + pessimistic locking on wallet balances
- ECDSA P-256 signature verification (Apple Secure Enclave compatible)

## Layered architecture (SOLID)

```
api/             → REST controllers, request/response DTOs
auth/            → JWT, refresh tokens, step-up, current user helpers
domain/
  user/          → User entity + service
  wallet/        → Wallet entity + service (locking, balance ops)
  transaction/   → Ledger entries
  qr/            → Rotating signed tokens + ZXing PNG generator
  biometric/     → Face ID enrol / challenge / verify
  cashin/        → Authorised cash-in locations
  audit/         → Tamper-evident audit log
payment/         → Charge / top-up orchestration
common/
  error/         → Exception types + global handler
  idempotency/   → Replay protection for write endpoints
config/          → Security, CORS, OpenAPI, properties
```

Every external boundary is an interface (e.g. `QrCodeImageGenerator`,
`JwtService`, `AuthService`) so implementations can be swapped (Dependency
Inversion). Read/write services live in domain packages with narrowly scoped
interfaces (Interface Segregation). Each entity owns its invariants — for
example `Wallet#applyDelta` enforces non-negative balance and ACTIVE status
(Single Responsibility).

## Running locally

### 1. Start PostgreSQL + the API with Docker Compose

```bash
cp .env.example .env
# generate real secrets:
echo "NEU_JWT_SECRET=$(openssl rand -base64 96)" >> .env
echo "NEU_QR_HMAC_SECRET=$(openssl rand -base64 48)" >> .env

docker compose up --build
```

API is now at `http://localhost:8080`. OpenAPI / Swagger UI at
`http://localhost:8080/swagger-ui.html`.

### 2. Run from your IDE / `mvn`

```bash
export JAVA_HOME=$HOME/.local/jvm/zulu21.50.19-ca-jdk21.0.11-macosx_aarch64/Contents/Home
mvn spring-boot:run
```

## Deploying to AWS

You already have an Aurora / RDS PostgreSQL cluster. Two paths:

### Path A — Elastic Beanstalk (Docker single-container)

1. Build + push the image to ECR:
   ```bash
   aws ecr create-repository --repository-name neupayment --region ap-southeast-1
   $(aws ecr get-login-password --region ap-southeast-1 | docker login \
       --username AWS --password-stdin <acct>.dkr.ecr.ap-southeast-1.amazonaws.com)
   docker build -t neupayment .
   docker tag neupayment:latest <acct>.dkr.ecr.ap-southeast-1.amazonaws.com/neupayment:latest
   docker push <acct>.dkr.ecr.ap-southeast-1.amazonaws.com/neupayment:latest
   ```
2. Edit `deploy/Dockerrun.aws.json` (replace `<your-account>`).
3. Zip the deploy folder and deploy via `eb deploy` or the EB console.
4. In the EB environment, configure these env vars (do NOT bake them into the
   image):
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (Aurora
     credentials — pull from Secrets Manager via the EB role)
   - `NEU_JWT_SECRET`, `NEU_QR_HMAC_SECRET`
   - `NEU_CORS_ALLOWED_ORIGINS=https://neu.edu.ph`

### Path B — ECS / Fargate

Use the same image. Ship `DB_*` and `NEU_*` env vars via a Task Definition
backed by AWS Secrets Manager. The API needs outbound access to the Aurora
endpoint and inbound on port 8080 from your ALB.

### Database

On first boot, Flyway runs `V1__init.sql` and `V2__seed_cash_in_locations.sql`
against the configured DB. The Aurora user must have `CREATE` permission on
the public schema (or pre-create the schema and use `flyway.schemas`).

## Security model

| Concern              | Mechanism                                                                   |
|----------------------|-----------------------------------------------------------------------------|
| Password storage     | BCrypt, configurable strength (default 12)                                  |
| Session              | Stateless JWT (HS512), 15-minute access TTL, 30-day refresh TTL             |
| Refresh token        | Random 48-byte URL-safe; only the SHA-256 hash is stored                    |
| Step-up auth         | Separate short-lived JWT (5 min) issued only after a verified Face ID sign  |
| Face ID 2FA          | iOS Secure Enclave generates ECDSA P-256 keypair; server verifies signature |
| QR rotation          | HMAC-SHA-256 signed payload; server stores nonce; one-shot consume          |
| Wallet integrity     | `@Version` + pessimistic write lock + DB CHECK constraint (`balance >= 0`)  |
| Money type           | `NUMERIC(19,4)` in Postgres, `BigDecimal` in Java                           |
| Audit trail          | Every login, top-up, charge, and admin op recorded in `audit_logs`          |
| Idempotency          | `idempotency_keys` table + `X-Idempotency-Key` header on writes             |
| Transport            | HSTS (1 year, includeSubDomains); CORS allow-list; secure headers           |
| Validation           | `@Valid` on every request DTO + `GlobalExceptionHandler`                    |

## Endpoint map (excerpt)

| Method | Path                                       | Auth         |
|--------|--------------------------------------------|--------------|
| POST   | `/api/v1/auth/register`                    | public       |
| POST   | `/api/v1/auth/login`                       | public       |
| POST   | `/api/v1/auth/refresh`                     | public       |
| POST   | `/api/v1/auth/logout`                      | bearer       |
| GET    | `/api/v1/wallets/me`                       | bearer       |
| GET    | `/api/v1/wallets/me/transactions`          | bearer       |
| GET    | `/api/v1/wallets/me/transactions/recent`   | bearer       |
| POST   | `/api/v1/qr/issue`                         | bearer       |
| GET    | `/api/v1/qr/me/image?mode=PAY_OUT`         | bearer (PNG) |
| POST   | `/api/v1/qr/redeem`                        | CASHIER/ADMIN|
| POST   | `/api/v1/payments/charge`                  | CASHIER/ADMIN|
| POST   | `/api/v1/payments/cash-topup`              | CASHIER/ADMIN|
| POST   | `/api/v1/biometric/enroll`                 | bearer       |
| POST   | `/api/v1/biometric/challenge`              | bearer       |
| POST   | `/api/v1/biometric/verify`                 | bearer       |
| GET    | `/api/v1/cash-in-locations`                | bearer       |
| GET    | `/api/v1/admin/users`                      | CASHIER/ADMIN|
| GET    | `/api/v1/admin/users/{id}`                 | CASHIER/ADMIN|
| GET    | `/api/v1/admin/users/{id}/wallet`          | CASHIER/ADMIN|
| POST   | `/api/v1/admin/topup`                      | CASHIER/ADMIN, step-up |
| POST   | `/api/v1/admin/staff`                      | ADMIN        |
| POST   | `/api/v1/admin/users/{id}/freeze`          | ADMIN        |
| POST   | `/api/v1/admin/users/{id}/reinstate`       | ADMIN        |

## Bootstrapping the first ADMIN

`/auth/register` always creates a STUDENT. To create the first cashier/admin
account, set these env vars on the very first deploy:

```
NEU_BOOTSTRAP_ADMIN_EMAIL=admin@neu.edu.ph
NEU_BOOTSTRAP_ADMIN_PASSWORD=<a strong password — at least 12 chars>
NEU_BOOTSTRAP_ADMIN_ID=NEU-ADMIN-0001
NEU_BOOTSTRAP_ADMIN_NAME="NEU System Admin"
```

On startup the app checks for an existing user with that email; if absent it
provisions a single ADMIN. After the first sign-in, this admin creates more
staff via `POST /api/v1/admin/staff` (CASHIER or ADMIN). **Remove the
bootstrap env vars from the EB / ECS environment after the first deploy.**

## Idempotent writes

Money-moving endpoints (`/payments/charge`, `/payments/cash-topup`,
`/admin/topup`) honour the `X-Idempotency-Key` header. Send the same key to
replay a call safely (the server returns the original response and does not
re-execute). Keys older than 7 days are swept by a background job.

Refer to `/swagger-ui.html` for the full schema.

## Build & test

```bash
mvn -DskipTests package      # produces target/neupayment.jar
mvn test                      # runs unit + slice tests
```

## Notes

- Hibernate runs with `ddl-auto=validate` in dev/prod — Flyway is the source
  of truth for schema. The `test` profile flips to `create-drop` for H2.
- `actuator/health` is the health probe path used by EB and the Docker
  `HEALTHCHECK`.
