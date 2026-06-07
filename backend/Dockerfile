# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /api

# Cache dependency resolution — only re-downloads when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and package, skipping tests and code-quality gates
# (quality gates run in CI, not inside the Docker build)
COPY src/ src/
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
USER plantpal

COPY --from=build /api/target/plantpal-backend-*.jar app.jar

EXPOSE 8080

# Container-aware JVM flags: respect cgroup memory limits
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
