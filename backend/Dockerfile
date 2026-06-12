# syntax=docker/dockerfile:1
# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /api

COPY pom.xml .
COPY src/ src/

# BuildKit cache mount: the /root/.m2 directory is reused across builds.
# Dependencies downloaded on the first build are cached; subsequent builds skip them.
# -DskipTests: tests run in CI, not inside the Docker image build.
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package \
      -DskipTests \
      -Dspotless.check.skip=true \
      -Dcheckstyle.skip=true \
      -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /api

# Non-root user — principle of least privilege
RUN addgroup -S plantpal && adduser -S plantpal -G plantpal
USER plantpal

COPY --from=build /api/target/plantpal-backend-*.jar app.jar

EXPOSE 8080

# Container-aware JVM flags: respect cgroup memory limits
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
