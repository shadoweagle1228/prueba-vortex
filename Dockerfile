FROM --platform=linux/arm64 eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app
COPY services/stock-price-adapter/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
