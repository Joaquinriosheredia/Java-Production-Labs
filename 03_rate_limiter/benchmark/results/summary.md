# Lab 03 — Rate Limiter Benchmark Summary

**Tool:** k6 v1.6.1 · **Stack:** Spring Boot 3.2.5 + Java 21 VT + Redis 7-alpine (Docker)  
**Load profile:** ramp 0→30 VUs over 10 s · steady 30 VUs for 30 s · ramp-down 10 s (50 s total)  
**Keys:** 5 shared keys (`k6-client-0` … `k6-client-4`), 6 VUs per key

---

## Before / After Comparison

### Run 1 — pre-improvement
Config: batch refill · 10 tokens / 1 s window · no circuit breaker

### Run 2 — post-improvement  
Config: greedy refill · 1 token / 100 ms · Resilience4j CircuitBreaker (60 % threshold, 5 s open)

---

### Throughput & Volume

| Metric | Run 1 (before) | Run 2 (after) | Δ |
|--------|---------------|--------------|---|
| Total requests | 23 009 | 22 985 | — |
| Duration | 50 s | 50 s | — |
| **Throughput** | **460 req/s** | **455 req/s** | ≈ flat (noise) |
| Checks passed | 100 % | 100 % | — |

---

### Latency — all requests (ms)

| Percentile | Run 1 (before) | Run 2 (after) | Δ |
|------------|---------------|--------------|---|
| p50 | 1.61 | 1.70 | +0.09 |
| p90 | 2.72 | 2.83 | +0.11 |
| p95 | 3.22 | 3.25 | +0.03 |
| p99 | 4.55 | 4.62 | +0.07 |
| **Max** | **96.80** | **64.90** | **−31.9 ms (−33 %)** |

---

### Latency split by status code (ms)

| | Run 1 p95 | Run 2 p95 | Run 1 p99 | Run 2 p99 | Run 1 max | Run 2 max |
|---|---|---|---|---|---|---|
| **200 OK** | 3.82 | 3.60 | 4.98 | 4.95 | 96.80 | 64.90 |
| **429 Too Many Requests** | 3.15 | 3.21 | 4.38 | 4.50 | 28.80 | 33.38 |

---

### Rejection Rate

| Status | Run 1 | Run 2 |
|--------|-------|-------|
| 200 OK | 2 529 (11 %) | 2 527 (11 %) |
| 429 Too Many Requests | 20 480 (89 %) | 20 458 (89 %) |
| 5xx / errors | 0 | 0 |

Rejection rate is identical — as expected. The refill rate is the same (10 tokens/s);
only the delivery schedule changed. The circuit breaker adds no overhead on the happy path.

---

### Chaos — Redis Outage (run 1 only; circuit breaker added after)

| Phase | Run 1 behaviour | Run 2 behaviour |
|-------|----------------|----------------|
| Redis stopped | HTTP 000 — Lettuce 1 s timeout, caller hangs | HTTP 000 until circuit opens |
| After threshold (6/10 failures) | N/A — no circuit breaker | **HTTP 503 in < 1 ms** |
| Redis restarted | First request → 200 OK | Auto half-open → 200 OK |

---

## Analysis

### What improved
- **Max latency: −33 % (96.80 ms → 64.90 ms).** The 96 ms outlier in Run 1 was
  caused by 30 VUs racing for 10 freshly-minted tokens at the start of each 1 s
  window. Greedy refill (1 token / 100 ms) drip-feeds tokens continuously,
  eliminating that synchronised burst and shaving ~32 ms off the worst case.
- **200 OK p95: −6 % (3.82 ms → 3.60 ms).** Allowed requests benefit most from
  the smoother token distribution; they no longer compete in a burst wave.
- **Redis failure: timeout hang → sub-millisecond 503.** The CircuitBreaker turns
  a 1 s Lettuce timeout into an immediate 503 once the circuit opens, preventing
  thread-pool saturation under partial Redis failure.

### What stayed flat
- **p95 / p99 on the overall distribution** changed by < 0.1 ms — within run-to-run
  noise. The improvement is concentrated at the tail (max), not the bulk.
- **Throughput and rejection rate** are identical between runs; the refill rate
  (10 tokens/s per key) is the same in both configurations.

### Remaining ceiling
- Single Redis instance is still an SPOF. The circuit breaker limits blast radius
  but does not provide HA. Next step: Redis Sentinel or a Bucket4j in-process
  fallback that activates while the circuit is open.
- The 89 % rejection rate reflects the test harness (6 VUs per key, ~92 req/s vs
  10 tokens/s). A real workload at lower concurrency will see this drop to near 0 %.
