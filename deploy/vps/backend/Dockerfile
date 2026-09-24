# Runtime-only image for the VPS. The regular backend/Dockerfile builds from source
# and needs the private contracts repo (D031); CI builds the JAR instead and copies
# it next to this file as app.jar, so this image only has to run it.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
