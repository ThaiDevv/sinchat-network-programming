# Build stage — use Java 25 JDK + install Maven manually
FROM eclipse-temurin:25-jdk-jammy AS build
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /app/target/render-java-server-1.0-SNAPSHOT.jar app.jar

# Render sets the PORT environment variable
EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
