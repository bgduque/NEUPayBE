# syntax=docker/dockerfile:1.7

# ---- build stage ---------------------------------------------------------
FROM azul-zulu:21 AS build
WORKDIR /workspace

# Install Maven
RUN apt-get update && apt-get install -y --no-install-recommends maven

# Copy everything at once to simplify the download process
COPY pom.xml ./
COPY src src

# Run the build directly. 
# We'll use --no-transfer-progress to keep the logs clean and 
# avoid timeouts caused by heavy log output.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package --no-transfer-progress

# # ---- build stage ---------------------------------------------------------
# FROM azul-zulu:21-jre AS build
# WORKDIR /workspace

# # Cache dependencies.
# RUN apt-get update && apt-get install -y --no-install-recommends maven
    
# # RUN --mount=type=cache,target=/root/.m2 \
# #     apt-get update && apt-get install -y --no-install-recommends maven \
# #     && mvn -B -q -DskipTests dependency:go-offline

# COPY pom.xml ./
# RUN --mount=type=cache,target=/root/.m2 \
#     mvn -B -q -DskipTests dependency:go-offline

# # Build.
# COPY src src
# RUN --mount=type=cache,target=/root/.m2 \
#     mvn -B -DskipTests package
# # COPY src src
# # RUN --mount=type=cache,target=/root/.m2 \
# #     mvn -B -DskipTests package

# ---- runtime stage -------------------------------------------------------
FROM azul-zulu:21-jre
WORKDIR /app

# 1. Stay as root for system installations
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

# 2. Create the user
RUN useradd -r -u 1001 -g root neupayment

# 3. Copy files and set permissions
COPY --from=build /workspace/target/*.jar app.jar
RUN chown -R neupayment:root /app && chmod g+rwX /app

# 4. NOW switch to the non-root user
USER 1001

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]