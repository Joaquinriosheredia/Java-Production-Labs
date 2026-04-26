# Benchmark — Lab 03: Rate Limiter

## Metric

**Rejection rate** and **p99 latency** under sustained overload.

---

## Run

```bash
docker compose -f docker/docker-compose.yml up -d
bash benchmark/run-benchmark.sh
```

---

## Expected Results

| Load | Token Budget | Expected Rejection Rate |
|------|-------------|------------------------|
| 5 RPS | 10 tokens/s | 0% (within budget) |
| 15 RPS | 10 tokens/s | ~33% (5/15 rejected) |
| 50 RPS | 10 tokens/s | ~80% |

Redis overhead per rate-limit check: <1ms at p99 on local network.
