# OPTIMIZED Dockerfile
# Optimizations applied:
#   1. Multi-stage build (JDK for build, JRE for runtime)
#   2. Layered JAR (Spring Boot 3.x) — dependencies cached separately from app code
#   3. Non-root user (security)
#   4. Alpine base (minimal OS footprint)
#   5. JVM flags for container-aware memory management

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Download dependencies first (layer cached unless pom.xml changes)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Build app
COPY app/src ./app/src
RUN ./mvnw package -DskipTests -q

# Extract layered JAR (Spring Boot layertools)
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layers in dependency order (least → most frequently changed)
# This maximizes Docker cache hits on code-only changes
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

USER appuser

EXPOSE 8088

# JVM flags:
#   -XX:+UseContainerSupport    — respect cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0   — use 75% of container memory for heap
#   -XX:+UseZGC                 — ZGC: low-latency GC for Java 21
#   -Djava.security.egd=...     — faster startup (avoid blocking entropy)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseZGC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]

# Result: ~200MB image, fast rebuilds (only application layer rebuilt on code change)
# docker build --target runtime -t lab09-optimized .
