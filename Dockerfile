# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY src src

# Make the wrapper executable and build the application JAR
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

# Stage 2: Create the minimal distroless runtime image
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder --chown=nonroot:nonroot /app/build/libs/*.jar app.jar

EXPOSE 8080

# Distroless requires the absolute path to the java executable, or just passing the arguments
# to the pre-configured java entrypoint
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "/app/app.jar"]
