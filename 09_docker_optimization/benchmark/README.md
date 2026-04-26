# Benchmark — Lab 09: Docker Optimization

## Metric

**Image size**, **build time**, and **rebuild time** (code-only change) for naive vs optimized Dockerfile.

---

## Run

```bash
bash benchmark/run-benchmark.sh
```

---

## Expected Results

| Metric | Naive | Optimized | Improvement |
|--------|-------|-----------|------------|
| Image size | ~520MB | ~195MB | 62% smaller |
| First build time | ~3m | ~4m (+multi-stage) | Similar |
| Rebuild (code change only) | ~3m (full rebuild) | ~15s (app layer only) | 12× faster |
| Run as root | Yes | No | Security ✓ |
| Container memory respect | No | Yes (-XX:+UseContainerSupport) | OOM prevention |
