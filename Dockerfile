# =============================================================================
# Dockerfile — Fuzzy Load Balancer Spring Boot Application
#
# Multi-stage build:
#   Stage 1 (builder):  Compiles and packages the application using Maven
#   Stage 2 (runtime):  Creates a minimal runtime image with only the JAR
#
# Why multi-stage?
#   The builder stage contains Maven, JDK, and source code (~500MB).
#   The runtime stage only contains the JRE and the JAR (~180MB).
#   This keeps the production image lean and secure.
#
# Build: docker build -t fuzzy-load-balancer .
# Run:   docker run -p 8080:8080 fuzzy-load-balancer
# =============================================================================

# ─── Stage 1: BUILD ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven wrapper and pom.xml first.
# Docker caches each layer. Copying pom.xml before source code means
# that Maven dependencies are only re-downloaded when pom.xml changes,
# NOT on every source code change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer — invalidated only on pom.xml change)
RUN ./mvnw dependency:go-offline -q

# Now copy the source code and build
COPY src/ src/

# Package the application, skipping tests (tests run in CI pipeline, not Docker build)
RUN ./mvnw package -DskipTests -q

# ─── Stage 2: RUNTIME ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create a non-root user for security (containers should never run as root)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the final JAR from the builder stage
# The wildcard handles the version number in the JAR filename
COPY --from=builder /app/target/fuzzy-load-balancer-*.jar app.jar

# Expose the application port
EXPOSE 8080

# JVM options:
#   -XX:+UseContainerSupport     — Respect container CPU/memory limits
#   -XX:MaxRAMPercentage=75.0    — Use up to 75% of container RAM for heap
#   -Djava.security.egd=...      — Speed up startup by using /dev/urandom for SecureRandom
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
