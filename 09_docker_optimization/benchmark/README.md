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

`bash benchmark/run-benchmark.sh` builds both images, then measures size (compressed and unpacked), user, startup (JVM uptime at ready, `STARTS=5`) and a code-only rebuild (`REBUILDS=3`; it changes the content of one Java file, since Docker caches `COPY` by content and `touch` would be a full cache hit). Results: [`results/summary.md`](results/summary.md); raw data in `results/raw/`.

Last run (2026-10-03): 247 → 94 MB compressed (−62%), 776 → 329 MB unpacked; root → `appuser`; startup 2.13 vs 2.15 s; code-only rebuild 9.10 vs 11.78 s.
