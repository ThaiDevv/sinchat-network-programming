# Build stage
FROM maven:3.9.6-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
# Note: Using 21 as a base for Maven build if 25 isn't in standard maven image yet, 
# but the target is 25. Let's try to find a 25 image if possible.
# Since it's 2026, eclipse-temurin:25 should exist.
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /app/target/render-java-server-1.0-SNAPSHOT.jar app.jar

# Render sets the PORT environment variable
EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
