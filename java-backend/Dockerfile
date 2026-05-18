# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install required tools
RUN apk add --no-cache curl bash docker-cli jq

# Copy JAR from build stage
COPY --from=build /build/target/*.jar app.jar

# Create data directory
RUN mkdir -p /app/data

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
