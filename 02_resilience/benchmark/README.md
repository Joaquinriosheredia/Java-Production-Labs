# Benchmark — Lab 02: Resilience

## Metric

**Error rate recovery time** and **fallback latency** when the external service fails at various rates.

---

## Run

```bash
./mvnw spring-boot:run  # in another terminal

bash benchmark/run-benchmark.sh
```

---

## Scenarios

| Scenario | Failure Rate | Expected Circuit State |
|----------|-------------|----------------------|
| Healthy | 0% | CLOSED |
| Degraded | 30% | CLOSED (retries absorb) |
| Critical | 80% | OPEN after 10 calls |
| Full outage | 100% | OPEN, all requests get fallback |

---

## Expected Results

- Retry adds ~600ms overhead (3 attempts × 200ms) on failure
- Circuit breaker opens after ≥5 failures in sliding window of 10
- When open: immediate fallback response (<5ms)
- When half-open: 3 probes, transitions to CLOSED or stays OPEN
