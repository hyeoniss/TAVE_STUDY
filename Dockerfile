FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring spring
WORKDIR /app
COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=12 \
  CMD curl --fail --silent "http://localhost:${PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
