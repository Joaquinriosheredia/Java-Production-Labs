# Lab 09 — Docker Optimization

## Problem

The naive Dockerfile produces an image that:
- Ships the full JDK and a single fat-JAR layer, so every code change rebuilds and pushes the whole jar
- Runs as root (security risk)
- Ignores container memory limits (→ OOM kills in Kubernetes)

**How do you build production-grade Java Docker images?**

---

## Architecture: Layer Strategy

```
Layer 1: eclipse-temurin:21-jre-alpine  (cached)
Layer 2: dependencies/                   (cached unless pom.xml changes)
Layer 3: spring-boot-loader/             (cached)
Layer 4: snapshot-dependencies/          (cached unless SNAPSHOT deps change)
Layer 5: application/                    (rebuilt on code change)
```

Code change → only Layer 5 rebuilds.

---

## Comparison

> **Not reproduced.** Neither committed Dockerfile builds from a clean checkout (`Dockerfile.naive`: `.dockerignore` excludes `target/`; `Dockerfile`: `dependency:go-offline` needs `com.labs:labs-common:0.0.1-SNAPSHOT`, which is in no repository). No size, startup or rebuild figure is published until it does.

| Property | Naive | Optimized |
|--------|-------|-----------|
| Runtime base | `eclipse-temurin:21-jdk` | `eclipse-temurin:21-jre-alpine` |
| Runs as | root | non-root (`appuser`) |
| Container memory aware | No | Yes |

---

## How to Run

```bash
# Build optimized
docker build -f docker/Dockerfile -t lab09-optimized .

# Run with memory limit
docker run -p 8088:8088 --memory=256m lab09-optimized

# Verify non-root
docker exec lab09-optimized whoami  # → appuser
```

---

## How to Break It

```bash
bash chaos/simulate-failure.sh
```

Demonstrates OOM when JVM ignores container memory limits.

---

## Key JVM Flags

```
-XX:+UseContainerSupport    # Read cgroup limits (not host memory)
-XX:MaxRAMPercentage=75.0   # Heap = 75% of container limit
-XX:+UseZGC                 # Java 21: low-latency GC
```

See [ADR-0001](docs/adr/ADR-0001.md).
