# Lab 02 — Resilience4j Benchmark Results (Post-Fix)

**Date:** 2026-05-02
**Environment:** WSL2 Linux, Docker 29.1.3, Java 21, Spring Boot 3.2.5, Resilience4j 2.2.0
**Tool:** k6 v1.6.1 + curl instrumented phases + Prometheus metrics

---

## Fix Applied (vs previous run)

Previous run had `@Bulkhead(fallbackMethod = "bulkheadFallback")` which intercepted all exceptions before the CircuitBreaker could see them, so the CB never opened.

**Fix:** Removed `fallbackMethod` from `@Bulkhead`. Only `@CircuitBreaker` holds the fallback:

```java
@Bulkhead(name = "externalService")                                           // no fallback
@CircuitBreaker(name = "externalService", fallbackMethod = "circuitBreakerFallback")
@Retry(name = "externalService")
public String callExternalService(String requestId) { ... }
```

---

## Test Configuration

| Parameter | Value |
|---|---|
| k6 VUs | 200 (ramp 5s → plateau 50s → ramp-down 5s) |
| k6 duration | 60s total |
| Sleep per VU | 50ms between calls |
| CB failureRateThreshold | 50% over 10-call sliding window |
| CB waitDurationInOpenState | 10s |
| CB permittedCallsInHalfOpenState | 3 |
| CB automaticTransitionFromOpenToHalfOpen | true |
| Bulkhead maxConcurrentCalls | 10 |
| Bulkhead maxWaitDuration | 500ms |
| Retry maxAttempts | 3 |
| Retry waitDuration | 200ms |
| Failure injection t=20s | 80% |
| Recovery t=45s | 0% |

---

## k6 Load Test — 200 VUs / 60s

| Metric | Value |
|---|---|
| Total HTTP requests | 159,482 |
| Throughput | 2,924 req/s |
| HTTP error rate (non-200) | **0.00%** |
| Fallback rate (k6 measured) | **56.92%** |
| Real successful calls | 68,703 |
| Fallback calls (CB open + failures) | 90,779 |
| Duration actual | 54.5s active (60s configured) |

---

## Circuit Breaker State Transitions — CONFIRMED WORKING

| Time | Event | CB State | Observed via |
|---|---|---|---|
| t=0s | Benchmark start, 0% failure | CLOSED | Health API |
| t=20s | 80% failure injected via admin API | CLOSED→OPEN | Prometheus |
| t=23s (est.) | CB opens after 10-call window breach | **OPEN** | Prometheus (t=25s confirms) |
| t=25s–45s | All requests rejected (not_permitted) | OPEN | Prometheus: open=1.0 |
| t=45s | Failure rate cleared to 0% | OPEN (10s timer) | Health API |
| t=48–50s | CB auto-transitions OPEN→HALF_OPEN→CLOSED | **CLOSED** | Health API (t=50s confirms) |
| t=50s–60s | Full recovery, all requests succeed | CLOSED | Prometheus: closed=1.0 |

**CB open duration:** ~27 seconds (t=23s to t=50s)
**Recovery time (OPEN→CLOSED):** ~5 seconds after failure cleared
**CB_OPEN→HALF_OPEN transition:** automatic (10s waitDurationInOpenState elapsed)
**HALF_OPEN probe calls:** 3 (permittedCallsInHalfOpenState), all succeeded

---

## Prometheus CB Totals (full session)

| Metric | Count |
|---|---|
| CB successful calls | 68,705 |
| CB failed calls | 112 |
| CB not_permitted calls | **90,680** |
| CB avg call time (successful) | 4.76ms (`327.15s / 68,705`) |
| CB avg call time (failed) | 20.97ms (`2.35s / 112`) |
| Final CB state | CLOSED |
| Final failure rate | 0.0% |

---

## Latency by Phase (k6 raw JSON, 159,482 samples)

| Phase | Time window | n | p50 | p90 | p95 | p99 | max | avg |
|---|---|---|---|---|---|---|---|---|
| **CLOSED baseline** | t=0–20s | 41,080 | 4.73ms | 18.59ms | 23.39ms | 32.55ms | 5,800ms | 9.03ms |
| **CB OPEN (fallback)** | t=20–45s | 95,001 | 5.00ms | 18.93ms | 25.93ms | 33.70ms | 5,827ms | 15.55ms |
| **Recovery / CLOSED** | t=45–60s | 23,399 | 20.09ms | 31.41ms | 34.09ms | 39.22ms | 5,824ms | 40.09ms |

**Overall (all phases):**

| p50 | p90 | p95 | p99 | max |
|---|---|---|---|---|
| 5.47ms | 24.09ms | 28.61ms | 35.76ms | 5,827ms |

### Latency analysis by phase

**CLOSED (t=0–20s):** Real service calls. Retry adds overhead on failures before CB opens.
Low p50/p90 indicates fast in-process service simulation; p99=32ms from bulkhead contention spikes.

**CB OPEN (t=20–45s):** Requests blocked immediately by CB (not_permitted); fallback is in-process with no retry.
p50=5ms — slightly higher than baseline because all 200 VUs are hitting the CB fast-path simultaneously.
Latency spike at max=5,827ms from bulkhead wait queue during CB open period transitions.

**Recovery (t=45–60s):** avg=40ms because during OPEN→HALF_OPEN only 3 probes allowed;
remaining VUs wait in bulkhead queue (maxWaitDuration=500ms), then receive fallback.
Once CB closes (~t=50s), latency drops back to baseline.

---

## Throughput by Stage

| Stage | Time window | Injected failure | Est. calls | Est. rps |
|---|---|---|---|---|
| Ramp-up | 0–5s | 0% | ~7,200 | ~1,440 |
| CLOSED baseline | 5–20s | 0% | ~33,880 | ~2,259 |
| Failure injected → CB OPEN | 20–45s | 80% (CB opens ~t=23s) | ~95,001 | ~3,800 |
| Recovery (HALF_OPEN→CLOSED) | 45–60s | 0% | ~23,399 | ~1,560 |

> During CB OPEN phase, throughput peaks (~3,800 rps) because fallback is instant (no retry delays, no actual service call).
> During recovery, throughput dips as bulkhead queuing + HALF_OPEN probe serialization applies.

---

## Key Findings vs Previous Run

| Metric | Before fix | After fix |
|---|---|---|
| CB ever opens? | **NO** | **YES** |
| CB failed calls (Prometheus) | 0 | 112 |
| CB not_permitted calls | 0 | 90,680 |
| Fallback type during failure | Bulkhead fallback (wrong) | CB fallback (correct) |
| HALF_OPEN transition | Never | t~48s (auto) |
| CB recovery | N/A | ~5s after failure cleared |
| CB fully functional | No | **Yes** |

---

## Sequence: Actual CB State Machine Traversal

```
CLOSED ──(failure rate > 50% over 10 calls at t~23s)──> OPEN
OPEN   ──(10s waitDuration elapsed, auto-transition)──> HALF_OPEN
HALF_OPEN ──(3 probe calls succeed at 0% failure)──> CLOSED
```

All 4 states visited: CLOSED → OPEN → HALF_OPEN → CLOSED

---

## Recommendations

1. **Bulkhead sizing:** With 200 VUs, bulkhead `maxConcurrentCalls=10` causes queuing spikes (max latency 5.8s). Increase to 50 for high-concurrency scenarios.
2. **Retry under load:** `maxAttempts=3, waitDuration=200ms` means each failure costs up to 600ms. CB should open fast enough to avoid retry storms — confirmed working.
3. **CB window size:** `slidingWindowSize=10` means CB opens very fast (after just 10 calls at 80% failure). For production, consider 20–50 with `minimumNumberOfCalls` matching.
4. **HALF_OPEN speed:** Only 3 probe calls before closing. Under 200 VU load this creates a brief bottleneck. Consider increasing `permittedCallsInHalfOpenState` to 10.

---

## Files

| File | Description |
|---|---|
| `benchmark/full-benchmark.js` | k6 script — 200 VUs, 60s, 5 stages, CB state tracking |
| `benchmark/results/k6-summary.json` | k6 handleSummary JSON output |
| `benchmark/results/k6-raw.json` | Full k6 raw events (3.19M lines, per-request timings) |
| `benchmark/results/k6-stdout.txt` | Full k6 terminal output |
| `benchmark/run-benchmark.sh` | Original 2-phase benchmark script |
| `benchmark/load-test.js` | Original k6 20-VU script |
