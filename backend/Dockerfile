# syntax=docker/dockerfile:1
# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /api

# contracts is a private repo with no package registry (D031). Its
# jsonschema2pojo codegen is unreliable inside Docker (see ai-gateway/Dockerfile
# for the fuller note), but builds reliably on the host, so rather than run
# that codegen in-image this copies the already-built artifact from the host's
# local .m2 (see the "contracts-m2" additional_context in docker-compose.yml,
# or --build-context on the CLI) straight into this image's own .m2 cache.
# Requires a "contracts-m2" build context pointing at
# ~/.m2/repository/io/platform/contracts/0.17.0 on the host — see DEPLOYMENT.md.
COPY --from=contracts-m2 . /root/.m2/repository/io/platform/contracts/0.17.0/

COPY pom.xml .
COPY src/ src/

# -DskipTests: tests run in CI, not inside the Docker image build.
RUN mvn clean package \
      -DskipTests \
      -Dspotless.check.skip=true \
      -Dcheckstyle.skip=true \
      -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /api

# Non-root user — principle of least privilege
RUN addgroup -S plantpal && adduser -S plantpal -G plantpal

# Pre-create the photo storage dir so its ownership (not root:root) is what
# Docker copies in as the initial content of the photos_data named volume.
RUN mkdir -p /tmp/plantpal/photos && chown -R plantpal:plantpal /tmp/plantpal/photos

USER plantpal

COPY --from=build /api/target/plantpal-backend-*.jar app.jar

EXPOSE 8080

# Container-aware JVM flags: respect cgroup memory limits
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
