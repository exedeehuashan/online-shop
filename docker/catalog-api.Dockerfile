FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /src
COPY pom.xml ./
COPY backend ./backend
RUN mvn -B -pl backend/catalog-api -am package -DskipTests
FROM eclipse-temurin:17.0.13_11-jre-alpine
RUN apk add --no-cache curl && addgroup -g 10001 app && adduser -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=build /src/backend/catalog-api/target/catalog-api-*.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=12 CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
