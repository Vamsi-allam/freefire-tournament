# syntax=docker/dockerfile:1

# ---- Build stage (uses Java 21) ----
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# Prime dependency cache
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline || true

# Build
COPY . .
RUN mvn -q -DskipTests package

# ---- Runtime stage (Java 21 JRE) ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create non-root user
RUN addgroup --system app \
    && adduser --system --ingroup app app

# Copy built jar
COPY --from=build /app/target/*.jar /app/app.jar

# Expose default app port (Render will set PORT env)
EXPOSE 8080

# Reasonable container JVM defaults
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENV PORT=8080

USER app

# Spring Boot reads server.port from PORT in application.properties
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
