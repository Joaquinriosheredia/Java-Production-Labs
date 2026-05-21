# Lab 08 — Kafka Streams Benchmark Summary

**Run date:** 2026-05-21  
**Topology:** `orders-stream` → filter(COMPLETED) → groupBy(userId) → TumblingWindow(60s) → count → `order-metrics`  
**Processing guarantee:** `at_least_once`

---

## Bugs Found and Fixed

Three bugs were discovered and fixed during the benchmark setup:

| # | Bug | Fix |
|---|-----|-----|
| 1 | `KafkaStreamsApplication` missing `@EnableKafkaStreams` — `StreamsBuilderFactoryBean` not registered | Added `@EnableKafkaStreams` annotation |
| 2 | `run-benchmark.sh` used HTTP GET on a POST-only endpoint | Fixed to `-X POST` |
| 3 | Docker `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9095` prevented `kafka-topics` CLI from connecting inside the container (external port not reachable from within) | Split into `INTERNAL://kafka:9092` (container) and `EXTERNAL://localhost:9095` (host) |

---

## Phase 1 — Startup

| Metric | Value |
|--------|-------|
| Kafka healthcheck | 3s |
| Spring Boot ready | 3s |
| Kafka Streams state | RUNNING |
| Total startup | ~6s |

Topics pre-created via `kafka-topics --bootstrap-server kafka:9092` using the internal listener.

---

## Phase 2 — Sanity Check (Unit Tests)

```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Build time: 3.4s | Driver: TopologyTestDriver (no broker)
```

Tests verified: completed orders produce metrics, PENDING orders are filtered, invalid JSON is skipped.

---

## Phase 3 — Baseline Load Test

**300 orders published sequentially to `orders-stream`:**

| Metric | Value |
|--------|-------|
| Throughput | **46.8 req/s** |
| p50 latency | **2ms** |
| p95 latency | **2ms** |
| p99 latency | **4ms** |
| Error rate | **0%** |
| Consumer lag | **0** (real-time) |
| Input messages consumed | 801 total |
| Window metrics produced | 128 |

The lag of 0 confirms Kafka Streams kept pace in real time. The 128 output metrics from 801 inputs reflects windowed aggregation: multiple orders per user per 60-second window collapse into a single count record.

---

## Phase 4 — Fault Injection

**Method:** `docker stop lab08-kafka` during active load (50 requests, Kafka stopped after request #10)

| Metric | Value |
|--------|-------|
| Requests before fault | 10 (100% OK) |
| Requests during fault | 40 |
| Successful during fault | **16 (40%)** |
| Failures/timeouts | **24 (60%)** |
| Probe request latency | **15,000ms** (curl hit max-time) |
| Probe HTTP code | **000** (timeout — no response) |
| App health during fault | **UP** ← false positive |
| Streams state during fault | **RUNNING** ← false positive |

### Key finding: false-positive health

Spring Actuator's `/actuator/health` returns `UP` even when Kafka is unreachable. The Streams state also shows `RUNNING` briefly — the in-memory state is not immediately reconciled after broker loss. **This means monitoring systems relying on health checks alone cannot detect a Kafka outage.**

### Key finding: synchronous producer blocks threads

`kafkaTemplate.send(...).get()` is synchronous. When Kafka is down, HTTP request threads block until the producer timeout fires or curl's `--max-time` is reached. This is effectively a thread-pool exhaustion risk under sustained Kafka outage.

### Why 16/40 requests succeeded during fault

The Kafka producer has an in-memory buffer. 16 requests hit the broker before the TCP connection fully dropped — they were successfully written and confirmed. The remaining 24 could not be persisted and their events are permanently lost (producer never confirmed delivery, no retry path).

---

## Phase 5 — Recovery Test

| Metric | Value |
|--------|-------|
| Kafka restart method | `docker compose up -d` |
| Time to Streams RUNNING | **< 195ms** |
| Consumer lag after recovery | **0** |
| Duplicate events detected | **None** |

**Post-recovery load (200 requests):**

| Metric | Value |
|--------|-------|
| Throughput | **109.7 req/s** |
| p50 latency | **2ms** |
| p95 latency | **3ms** |
| p99 latency | **4ms** |
| Error rate | **0%** |

Recovery is near-instant because Kafka Streams restores state from the changelog topic (`KSTREAM-AGGREGATE-STATE-STORE-...-changelog`). No full replay required; the committed offset was preserved.

---

## Comparative Summary

| Phase | Throughput | p99 | Error Rate | Consumer Lag |
|-------|-----------|-----|-----------|--------------|
| **Baseline** | 46.8 rps | 4ms | 0% | 0 |
| **During Fault** | ~1.6 rps (16/40 in 10s window) | 15,000ms | 60% | N/A (Kafka down) |
| **Post-Recovery** | 109.7 rps | 4ms | 0% | 0 |

---

## Analysis

### Bottlenecks

1. **HTTP client throughput ceiling**: The benchmark uses sequential curl requests. The actual Kafka ingestion throughput is not the bottleneck — the sequential HTTP client is.
2. **Synchronous producer**: `kafkaTemplate.send().get()` ties HTTP threads to Kafka availability. Under Kafka outage, threads accumulate.
3. **No circuit breaker**: The producer path has no fallback. Dead requests block until timeout, degrading HTTP server capacity.

### Trade-offs: `at_least_once` vs `exactly_once`

| | at_least_once (current) | exactly_once |
|--|------------------------|--------------|
| Overhead | Low | Higher (EOS protocol, 2-phase) |
| Latency | Lower | +5–20ms per batch |
| Duplicates on recovery | Possible | Guaranteed none |
| Window correctness | May overcount if duplicates | Always correct |
| Config | `processing.guarantee=at_least_once` | `exactly_once_v2` |

With this topology (stateful count aggregation), duplicates from `at_least_once` inflate window counts. The current run showed **no duplicates** because Kafka was stopped cleanly before producer confirmation — events were lost, not duplicated. A fast-restart scenario could produce duplicates.

### Impact of Lag on Windows

With consumer lag consistently at 0, the 60-second tumbling window reflects real-time data. Late events (outside the window after a lag spike) would be **silently discarded** since `ofSizeWithNoGrace` is used with no grace period. Under high lag, this produces systematically undercounted windows — a data quality risk that is invisible without lag monitoring.

### Recommendations

1. **Add Kafka health indicator**: Override or extend `actuator/health` to probe Kafka connectivity.
2. **Switch to async producer**: Remove `.get()` from `kafkaTemplate.send()` to prevent thread blocking; add proper error callback.
3. **Add circuit breaker**: Use Resilience4j or similar to fail fast when Kafka is down, returning HTTP 503 immediately.
4. **Consider grace period**: `TimeWindows.ofSizeAndGrace(Duration.ofSeconds(60), Duration.ofSeconds(5))` tolerates modest late arrivals.
5. **Monitor changelog topic**: The state store changelog is the recovery anchor — ensure it has adequate retention.

---

## Final State

| Metric | Value |
|--------|-------|
| `orders-stream` total offset | 1,040 |
| `order-metrics` total offset | 199 |
| Consumer lag | 0 |
| Streams state | RUNNING |
