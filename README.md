# Java Production Labs

Ten real, self-contained Spring Boot 3 + Java 21 labs that demonstrate
production engineering decisions — concurrency, resilience, messaging, persistence, and deployment.
Each lab is runnable from cold start, measurable under load, and has a documented failure mode.

---

## CI Status

![CI](https://github.com/Joaquinriosheredia/Java-Production-Labs/actions/workflows/ci.yml/badge.svg)

---

## Stack

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)
![Docker](https://img.shields.io/badge/Docker-24+-blue?logo=docker)
![Testcontainers](https://img.shields.io/badge/Testcontainers-ready-blue)
![k6](https://img.shields.io/badge/k6-load--testing-purple)
![Prometheus](https://img.shields.io/badge/Prometheus-monitoring-orange)
![Grafana](https://img.shields.io/badge/Grafana-dashboards-yellow)

---

## Quick Start

```bash
git clone https://github.com/Joaquinriosheredia/Java-Production-Labs
cd Java-Production-Labs/01_virtual_threads
# Most labs depend on the shared test module, which is not published: install it once
./mvnw -B -f ../common/pom.xml install -DskipTests
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run
# Verify: http://localhost:8080/api/v1/threads/info
```

See `make help` for all available commands.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | SDKMAN: `sdk install java 21-tem` |
| Maven | 3.8.7 | Included via the `./mvnw` wrapper (pinned in `.mvn/wrapper/maven-wrapper.properties`) |
| Docker | 24+ | With Compose v2 |
| k6 | latest | For benchmark scripts |
| Make | any | Optional — convenience wrapper |

---

## Labs

| # | Lab | Core Concept | Port | Status | Benchmark results |
|---|-----|-------------|------|--------|:--------------:|
| 01 | [Virtual Threads](01_virtual_threads/) | Concurrency with Project Loom | 8080 | ✅ | [results](01_virtual_threads/benchmark/results/summary.md) |
| 02 | [Resilience](02_resilience/) | Circuit Breaker, Retry, Bulkhead | 8081 | ✅ | [results](02_resilience/benchmark/summary.md) |
| 03 | [Rate Limiter](03_rate_limiter/) | Distributed token bucket (Redis) | 8082 | ✅ | [results](03_rate_limiter/benchmark/results/summary.md) |
| 04 | [Transactional Outbox](04_outbox_kafka/) | At-least-once event delivery | 8083 | ✅ | [results](04_outbox_kafka/benchmark/results/summary.md) |
| 05 | [Saga Pattern](05_saga_pattern/) | Distributed transactions + compensation | 8084 | ✅ | [results](05_saga_pattern/benchmark/results/summary.md) |
| 06 | [Redis vs Kafka](06_redis_vs_kafka/) | Messaging trade-off benchmark | 8085 | ✅ | [results](06_redis_vs_kafka/benchmark/results/summary.md) |
| 07 | [PostgreSQL Tuning](07_postgres_tuning/) | Partial indexes, EXPLAIN ANALYZE | 8086 | ✅ | [results](07_postgres_tuning/benchmark/results/summary.md) |
| 08 | [Kafka Streams](08_kafka_streams/) | Real-time windowed aggregation | 8087 | ✅ | [results](08_kafka_streams/benchmark/results/summary.md) |
| 09 | [Docker Optimization](09_docker_optimization/) | Layered JARs, multi-stage build | 8088 | ✅ | [results](09_docker_optimization/benchmark/results/summary.md) |
| 10 | [Kubernetes Autoscaling](10_kubernetes_autoscaling/) | HPA on custom Prometheus metrics | 8089 | ✅ | [results](10_kubernetes_autoscaling/benchmark/results/summary.md) |

---

## Quality Matrix

Benchmark: ✅ only where a results file is versioned in `<lab>/benchmark/`.
Testcontainers: ✅ only where a test actually starts a container (labs 03–07). Labs 01, 02, 08, 09 and 10 declare the dependency but no test uses it.

| Lab | ADR | Tests | Testcontainers | Benchmark | Metrics | Chaos |
|-----|-----|-------|----------------|-----------|---------|-------|
| 01 | ✅ | ✅ | — | ✅ | ✅ | ✅ |
| 02 | ✅ | ✅ | — | ✅ | ✅ | ✅ |
| 03 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 04 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 05 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 06 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 07 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 08 | ✅ | ✅ | — | ✅ | ✅ | ✅ |
| 09 | ✅ | ✅ | — | ✅ | ✅ | ✅ |
| 10 | ✅ | ✅ | — | ✅ | ✅ | ✅ |

---

## Security & Quality Fixes

Seven fixes, found with java-vibe-guard and a security review. The scan output is not versioned; two items (MDC logging, KRaft migration) are improvements rather than bugs:

- [WorkloadController](10_kubernetes_autoscaling/app/src/main/java/com/labs/k8s/controller/WorkloadController.java#L29): DoS via unbounded `workMs` parameter (`@Min`/`@Max` added)
- [SagaOrderService](05_saga_pattern/app/src/main/java/com/labs/saga/service/SagaOrderService.java#L45): Kafka send without timeout → thread starvation (`.get(5s)`)
- [StreamController](08_kafka_streams/app/src/main/java/com/labs/kafkastreams/controller/StreamController.java#L53): Kafka send without timeout → thread starvation (`.get(5s)`)
- [OrderSagaOrchestrator](05_saga_pattern/app/src/main/java/com/labs/saga/saga/OrderSagaOrchestrator.java#L59): 6 `@KafkaListener` without `@RetryableTopic` → silent message loss
- [docker-compose.yml](docker-compose.yml): Zookeeper deprecated → migrated to KRaft mode
- [OrderController](04_outbox_kafka/app/src/main/java/com/labs/outbox/controller/OrderController.java#L40) + [SagaController](05_saga_pattern/app/src/main/java/com/labs/saga/controller/SagaController.java#L45): MDC logging added for observability
- [OrderController](04_outbox_kafka/app/src/main/java/com/labs/outbox/controller/OrderController.java#L34) + [SagaController](05_saga_pattern/app/src/main/java/com/labs/saga/controller/SagaController.java#L40): Log injection via MDC sanitized (CWE-117)

---

## Real Benchmark Results

Every figure below comes from a versioned results file, linked in each section. Raw data
(k6 output, CSV, JSON, query plans) is versioned for labs 01, 07, 09 and 10, and for part of
lab 02 (k6 only); for labs 03, 04, 05, 06 and 08 the results file is a written summary
without raw data. Labs 01, 07 and 09 were re-run on 2026-10-03 with the repo's own
`run-benchmark.sh`; their results record environment, date and commit.

### Lab 01 — Virtual Threads

Median (min – max) over 5 runs per mode and load, 200 tasks × 100 ms per request — [results](01_virtual_threads/benchmark/results/summary.md).
The two loads are separate experiments: compare modes within a load, not across loads.

| VUs | Metric | Platform Threads (pool = 20) | Virtual Threads |
|----:|--------|:----------------:|:---------------:|
| 50 | Throughput | 36.7 req/s (36.6 – 37.5) | **207.1 req/s** (201.7 – 208.0) |
| 50 | p50 latency | 1,008 ms (1,008 – 1,008) | **102 ms** (101 – 102) |
| 50 | p99 latency | 1,020 ms (1,019 – 1,026) | **105 ms** (104 – 112) |
| 200 | Throughput | 117.5 req/s (116.5 – 119.1) | **827.5 req/s** (808.2 – 830.4) |
| 200 | p50 latency | 1,264 ms (1,254 – 1,271) | **101 ms** (101 – 101) |
| 200 | p99 latency | 1,731 ms (1,706 – 1,735) | **107 ms** (106 – 109) |

Virtual threads eliminate the pool-size bottleneck under I/O-bound concurrency.
No reactive programming required.

### Lab 02 — Resilience4j

Source: [results](02_resilience/benchmark/summary.md).

| Metric | Value |
|--------|-------|
| Circuit OPEN duration | ~27 s (approximate: the opening time, t≈23 s, is estimated) |
| Calls rejected while OPEN (`not_permitted`) | 90,680 |
| Recovery (OPEN → CLOSED) | automatic, ~5 s after the failure cleared (approximate) |

The timeline comes from the written summary, not from versioned Prometheus data. With
`waitDurationInOpenState=10s` the breaker would also have probed HALF_OPEN while the failure
rate was still 80 %; the summary does not record those transitions.

Bulkhead + CircuitBreaker + Retry chain measured end-to-end under injected failure rate.

### Lab 03 — Redis Rate Limiter

Source: [results](03_rate_limiter/benchmark/results/summary.md).

| Metric | Value |
|--------|-------|
| Requests handled | 460 req/s, of which 89 % rejected with 429 (≈ 50 req/s allowed, 2,529 in 50 s) |
| p99 latency (all requests) | 4.55 ms |

Distributed token bucket in Redis. The Redis-outage behaviour with the circuit breaker
(HTTP 503 once the breaker opens) was not measured: the chaos test ran before the breaker was
added. Behaviour with several app instances is not tested.

### Lab 04 — Transactional Outbox

Source: [results](04_outbox_kafka/benchmark/results/summary.md).

| Metric | Value |
|--------|-------|
| Throughput | 163.8 req/s |
| Events left FAILED after a Kafka kill (after the fix) | 0 (before the fix: 5 of 91) |
| Drain rate (tuned poller, after the fix) | 88.9 → 149.8 events/s |

Chaos testing found a bug: events marked FAILED during a Kafka outage were never retried
(`findPendingEvents()` only read `PENDING`). It was fixed with retries and backoff.
The outbox pattern prevents the dual-write race condition at the DB transaction boundary.

### Lab 05 — Saga Pattern

Source: [results](05_saga_pattern/benchmark/results/summary.md).

| Metric | Value |
|--------|-------|
| Orders stuck in `STARTED` (baseline load) | 71.8% (1,165 / 1,622), with 0% HTTP errors |
| Race condition demonstrated | dual-write without saga |
| During the Kafka outage | 35 of 50 requests failed (HTTP 000/500), 15 accepted (202) |

Measured on 2026-05-15, before later code changes to this lab (blocking `.get()` removed from
the orchestrator, 5 s send timeout, `@RetryableTopic`, listener containers on platform
threads). It has not been re-run since, so these figures do not describe the current code.

Choreography-based saga over Kafka. The benchmark intentionally demonstrates the failure mode —
services appearing healthy while distributed state is inconsistent.

### Lab 06 — Redis Pub/Sub vs Kafka

Source: [results](06_redis_vs_kafka/benchmark/results/summary.md).

| Metric | Redis Pub/Sub | Kafka |
|--------|:-------------:|:-----:|
| Throughput (default API, 1,000 msgs, best of 3) | 2,227 msg/s | 47,619 msg/s (enqueue: 21 ms) |
| Messages sent during broker crash, lost | **200 / 200** | **200 / 200** (producer `delivery.timeout.ms` 10 s) |
| Messages committed before the crash | — (no persistence) | intact, replayed after restart |

The throughput gap reflects an API asymmetry: Redis `convertAndSend()` blocks per-message
for a full TCP round-trip (~419 µs); Kafka `send()` enqueues to an in-memory buffer (~7 µs)
and flushes asynchronously. Neither backend delivered what was sent while its broker was down;
Kafka keeps what was committed before the crash, Redis keeps nothing.

### Lab 07 — PostgreSQL Optimization

Median (min – max) over 10 runs — [results](07_postgres_tuning/benchmark/results/summary.md).

| Metric | Sequential scan | Partial index (Index Scan) |
|--------|:------:|:-----:|
| Query latency | 7.45 ms (6.32 – 29.56) | **1.52 ms** (1.11 – 4.71) |
| Ratio of medians | — | **4.9×** |

100K-row table (5,084 PENDING), `ANALYZE` after seeding, partial index on `occurred_at` WHERE `status = 'PENDING'`.
Plans (`EXPLAIN (ANALYZE, BUFFERS)`) and table scan counters are versioned with the results.

### Lab 08 — Kafka Streams

Source: [results](08_kafka_streams/benchmark/results/summary.md).

| Metric | Baseline | Post-Recovery |
|--------|:--------:|:-------------:|
| Throughput (sequential curl client) | 46.8 rps (300 requests) | 109.7 rps (200 requests) |
| p99 latency | **4 ms** | **4 ms** |
| Error rate | **0%** | **0%** |
| Consumer lag | **0** | **0** |
| Time to Streams `RUNNING` after Kafka restart | — | < 195 ms (no full replay; committed offset preserved) |

The throughput difference comes from the sequential curl client and the different request
counts, not from the recovery. There is no raw data, only the written summary.

Critical finding: `actuator/health` reports `UP` while Kafka is unreachable, and the Streams state briefly still shows `RUNNING` — standard health probes are false positives.
During the Kafka outage (measured 2026-05-21, before the 5 s send timeout was added): `kafkaTemplate.send().get()` blocked HTTP threads; 60% of requests failed (24/40) at the 15 s curl `--max-time`.
`at_least_once` guarantee with no duplicates on clean restart; lag=0 immediately after broker recovery.

### Lab 09 — Docker Optimization

Both images built from this repo — [results](09_docker_optimization/benchmark/results/summary.md). Times: median (min – max).

| Metric | Naive Image | Optimized Image |
|--------|:-----------:|:---------------:|
| Runtime base | `eclipse-temurin:21-jdk` | `eclipse-temurin:21-jre-alpine` |
| Image size, compressed (pull/push) | 247 MB | **94 MB** (−62%) |
| Image size, unpacked on disk | 776 MB | **329 MB** |
| Runs as | root | **non-root** (`appuser`, uid 100) |
| Startup (JVM uptime at ready, 5 runs) | 2.13 s (2.10 – 2.14) | 2.15 s (2.07 – 2.22) |
| Code-only rebuild (3 runs, warm cache) | 9.10 s (8.98 – 9.32) ¹ | 11.78 s (10.82 – 12.45) ² |

¹ `./mvnw package` on the host + `docker build`. ² `docker build` only (the jar is compiled in the build stage).
The optimized image is smaller and non-root; it does not start faster, and its code-only rebuild is not faster on this machine.

### Lab 10 — Kubernetes HPA

Source: [results](10_kubernetes_autoscaling/benchmark/results/summary.md).

- With 50 VUs, each request sleeping 200 ms on a virtual thread, steady-state CPU was ~110m
  (samples between 68m and 240m after warm-up). The CPU-only HPA did not scale up.
- The HPA target is 70 % of the CPU **request** (200m), i.e. 140m, not 70 % of the 500m limit:
  steady-state CPU was close to, not far below, the target.
- Error rate 0 % with 1 pod during the load test; the pod-kill test ran with 2 pods.
- Pod replacement reached `Running` 5 s after the kill (observed). Readiness at ~25 s is
  computed from the probe settings (20 s + 5 s), not observed.
- The workload is `Thread.sleep`, which would not use CPU on platform threads either; there is
  no platform-thread control, so this does not show that virtual threads are the cause.
- An HPA on the custom metric `lab_active_requests_gauge` is defined in `scripts/k8s/hpa.yml`
  but was not run: the benchmark applies a CPU-only HPA and prometheus-adapter is not installed.

---

## What Each Lab Demonstrates

### 01 · Virtual Threads
Java 21 Project Loom against a 20-thread platform pool, I/O-bound load (medians of 5 runs):
207 vs 37 req/s at 50 VUs, 828 vs 118 req/s at 200 VUs; virtual-thread p99 stays near 105 ms at both loads.
No reactive programming needed.

### 02 · Resilience
Resilience4j 2.x: composable `Bulkhead → CircuitBreaker → Retry`.
Inject failure rate via REST API, watch circuit transition `CLOSED → OPEN → HALF_OPEN → CLOSED`.
All state visible in `/actuator/health` and Prometheus.

### 03 · Rate Limiter
Distributed token bucket backed by Redis. Each API client gets an isolated bucket.
Designed to share state across app instances through Redis (not tested with more than one instance).
Returns `Retry-After` and `X-RateLimit-Remaining` headers.

### 04 · Transactional Outbox
Order + outbox event written in one DB transaction.
Kill Kafka mid-flight: orders are created without errors, events queue in the outbox.
When Kafka recovers the poller drains the backlog; events that failed are retried with backoff.

### 05 · Saga Pattern
Choreography-based saga over Kafka topics.
Inject inventory failure: watch `STARTED → PAYMENT_APPROVED → INVENTORY_FAILED → COMPENSATED`.
Full state machine visible in a single DB query.

### 06 · Redis vs Kafka
Side-by-side benchmark: Redis Pub/Sub (2,227 msg/s, ephemeral) vs Kafka (47,619 msg/s async, durable).
During a broker crash both lose what is sent (200/200); Kafka replays what was committed before it, Redis has nothing to replay.
The throughput gap is an API asymmetry, not a speed claim — see benchmark results for full analysis.

### 07 · PostgreSQL Tuning
100K rows, 5% PENDING. Sequential scan: 7.45 ms. Partial index (Index Scan): 1.52 ms. 4.9× (medians of 10 runs).
`EXPLAIN (ANALYZE, BUFFERS)` of both plans versioned in `benchmark/results/raw/`.

### 08 · Kafka Streams
Tumbling 60-second window counting orders per user. 46.8 rps baseline, p99=4ms, lag=0.
Kafka broker killed mid-load: 60% error rate (24/40), health check false-positive (reports UP while Kafka is down).
Streams back to RUNNING < 195 ms after the broker restart (no full replay; committed offset preserved) — no duplicates, lag=0 immediately after restart.

### 09 · Docker Optimization
Naive image: full JDK, single fat-JAR layer, runs as root.
Optimized image: multi-stage build, layered JAR, JRE on Alpine, non-root (`appuser`), `-XX:MaxRAMPercentage=75.0`.
247 → 94 MB compressed (−62%); same startup; see the results for rebuild times.

### 10 · Kubernetes Autoscaling
HPA v2 manifest with a custom Prometheus metric `lab_active_requests_gauge` (defined, not exercised by the benchmark).
In the benchmark, the CPU-only HPA did not scale up under a sleep-bound load.
Graceful shutdown: in-flight requests complete before pod terminates.

---

## Running All Tests

```bash
./01_virtual_threads/mvnw -B -f common/pom.xml install -DskipTests   # once: shared test module
make test-all
```

Or per lab:
```bash
cd 01_virtual_threads && ./mvnw verify   # after installing common/ as above
```

Labs 03–07 run their integration tests against real PostgreSQL, Redis and Kafka with Testcontainers.

---

## Observability

All labs expose:
- `/actuator/health` — liveness + readiness probes
- `/actuator/prometheus` — Prometheus metrics scrape endpoint
- `/actuator/metrics` — Spring metrics

Shared Grafana + Prometheus stack:
```bash
docker compose up -d  # starts shared postgres, redis, kafka, prometheus and grafana
# Grafana: http://localhost:3000  (admin/admin)
# Prometheus: http://localhost:9090
```

Lab 01 includes a pre-built Grafana dashboard: `01_virtual_threads/docker/grafana/dashboards/lab01-virtual-threads.json`.

---

## For Recruiters / Technical Evaluators

This repository is designed to be evaluated, not just read:

| Signal | Where to look |
|--------|--------------|
| Technical decisions with trade-offs | `<lab>/docs/adr/ADR-0001.md` |
| Production-grade tests | `<lab>/app/src/test/` |
| Real infrastructure in tests | Testcontainers in labs 03–07 |
| Measurable performance claims | `<lab>/benchmark/README.md` |
| Failure scenarios | `<lab>/chaos/simulate-failure.sh` |
| CI pipeline | `.github/workflows/ci.yml` |

---

## How this was built

This repository was developed with AI assistance (Claude) for scaffolding, code generation, and test setup. All labs have been reviewed, compiled and tested locally by the author.

Every benchmark figure in this README links to a versioned results file in `<lab>/benchmark/` (raw data only for some labs; see *Real Benchmark Results*). Every architectural decision is documented in the ADRs.

---

## License

MIT
