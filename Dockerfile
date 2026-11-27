FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew build -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-Xmx200m", "-XX:MaxMetaspaceSize=160m", "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar"]
