# syntax=docker/dockerfile:1.7
#
# Imagen multi-stage para stock-price-adapter.
#
# La etapa de build compila con el JDK del host de build (rápido, sin emulación)
# y la etapa final usa una JRE Alpine ARM64 — el runtime objetivo son nodos
# Graviton (EKS) — reduciendo superficie de ataque y tamaño de imagen.

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY services ./services

RUN chmod +x gradlew \
    && ./gradlew :services:stock-price-adapter:bootJar --no-daemon --console=plain

FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre-alpine AS runtime

ARG VERSION=0.1.0
LABEL org.opencontainers.image.title="stock-price-adapter" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.vendor="Andes Retail"

RUN addgroup -S app && adduser -S -G app -u 1001 app \
    && apk add --no-cache curl

WORKDIR /app

COPY --from=build --chown=app:app /workspace/services/stock-price-adapter/build/libs/*.jar app.jar

USER 1001

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
