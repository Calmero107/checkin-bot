# ===== Stage 1: Build JAR =====
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven
RUN mvn clean package -DskipTests

# ===== Stage 2: Run App =====
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=builder /app/target/*-full.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

