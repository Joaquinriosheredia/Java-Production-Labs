# Lab 09 — Docker Optimization

## Problem

The naive Dockerfile produces a 520MB image that:
- Takes 3+ minutes to rebuild on every code change
- Runs as root (security risk)
- Ignores container memory limits (→ OOM kills in Kubernetes)

**How do you build production-grade Java Docker images?**

---

## Architecture: Layer Strategy

```
Layer 1: eclipse-temurin:21-jre-alpine  (cached, ~180MB)
Layer 2: dependencies/                   (cached unless pom.xml changes, ~60MB)
Layer 3: spring-boot-loader/             (cached, ~500KB)
Layer 4: snapshot-dependencies/          (cached unless SNAPSHOT deps change)
Layer 5: application/                    (rebuilt on code change, ~2MB)
```

Code change → only Layer 5 rebuilds → 15s vs 3 minutes.

---

## Comparison

| Metric | Naive | Optimized |
|--------|-------|-----------|
| Image size | ~520MB | ~195MB |
| Rebuild (code change) | ~3min | ~15s |
| Runs as root | Yes | No |
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
