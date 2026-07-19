# syntax=docker/dockerfile:1.7

# Docker Official Image, multi-platform index (linux/amd64 and linux/arm64).
FROM maven:3.9-eclipse-temurin-17@sha256:1ed5d1f54416b706707b4f3238f63a20bb06aab27c6d240090a2bb9ad895ed45 AS builder

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Dmaven.test.skip=true package

# Google Distroless supported Java 17 multi-platform nonroot index.
FROM gcr.io/distroless/java17-debian13:nonroot@sha256:81d09cac6ec47f6a13c61a941557f95079213320f3ddbf9d353de9317669aab5 AS runtime

ARG VCS_REF=unknown
ARG VERSION=0.0.1-SNAPSHOT
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="CodeRushOJ Backend" \
      org.opencontainers.image.description="CodeRushOJ Spring Boot business API" \
      org.opencontainers.image.source="https://github.com/CodeRushOJ/croj-backend" \
      org.opencontainers.image.url="https://github.com/CodeRushOJ/croj-backend" \
      org.opencontainers.image.documentation="https://github.com/CodeRushOJ/croj-backend#production-container" \
      org.opencontainers.image.revision="$VCS_REF" \
      org.opencontainers.image.version="$VERSION" \
      org.opencontainers.image.created="$BUILD_DATE" \
      org.opencontainers.image.base.name="gcr.io/distroless/java17-debian13:nonroot" \
      org.opencontainers.image.base.digest="sha256:81d09cac6ec47f6a13c61a941557f95079213320f3ddbf9d353de9317669aab5"

WORKDIR /app
COPY --from=builder --chown=65532:65532 /workspace/target/croj.jar /app/croj.jar
COPY --from=builder --chown=65532:65532 \
    /workspace/target/classes/com/coderushoj/container/ActuatorHealthCheck.class \
    /app/healthcheck/com/coderushoj/container/ActuatorHealthCheck.class

ENV SPRING_PROFILES_ACTIVE=prod \
    TMPDIR=/tmp \
    FILE_UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/tmp"

USER 65532:65532
EXPOSE 7999

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 CMD ["/usr/bin/java","-cp","/app/healthcheck","com.coderushoj.container.ActuatorHealthCheck"]
ENTRYPOINT ["/usr/bin/java","-jar","/app/croj.jar"]
