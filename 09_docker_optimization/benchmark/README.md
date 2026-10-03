# Benchmark — Lab 09: Docker Optimization

## Metric

**Image size**, **build time**, and **rebuild time** (code-only change) for naive vs optimized Dockerfile.

---

## Run

```bash
bash benchmark/run-benchmark.sh
```

---

## Results

> **Not reproduced.** Neither committed Dockerfile builds from a clean checkout (`Dockerfile.naive`: `.dockerignore` excludes `target/`; `Dockerfile`: `dependency:go-offline` needs `com.labs:labs-common:0.0.1-SNAPSHOT`, which is in no repository). No size, startup or rebuild figure is published until it does.

Also: `run-benchmark.sh` simulates a code change with `touch`, but Docker caches `COPY` by content, not mtime, so that rebuild is a full cache hit and does not measure a code-only rebuild.
