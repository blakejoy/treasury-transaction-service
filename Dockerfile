# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies first for faster rebuilds
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user
RUN useradd --system --no-create-home appuser
USER appuser

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
# Let the JVM size the heap from the container's memory limit (defaults to a conservative 25%).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]