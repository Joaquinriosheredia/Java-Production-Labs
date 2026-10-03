# Java Production Labs

**Clone it, run it, demo it in 45 minutes.**

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
| Maven | 3.9+ | Included via `./mvnw` wrapper |
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
| 09 | [Docker Optimization](09_docker_optimization/) | Layered JARs, multi-stage build | 8088 | ✅ | not reproduced |
| 10 | [Kubernetes Autoscaling](10_kubernetes_autoscaling/) | HPA on custom Prometheus metrics | 8089 | ✅ | [results](10_kubernetes_autoscaling/benchmark/results/summary.md) |

---

## Quality Matrix

Benchmark: ✅ only where a results file is versioned in `<lab>/benchmark/` (Lab 09: not reproduced, see below).
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
| 09 | ✅ | ✅ | — | — | ✅ | ✅ |
| 10 | ✅ | ✅ | — | ✅ | ✅ | ✅ |

---

## Security & Quality Fixes

7 real bugs found and fixed using java-vibe-guard + security-guidance analysis:

- [WorkloadController](10_kubernetes_autoscaling/app/src/main/java/com/labs/k8s/controller/WorkloadController.java#L29): DoS via unbounded `workMs` parameter (`@Min`/`@Max` added)
- [SagaOrderService](05_saga_pattern/app/src/main/java/com/labs/saga/service/SagaOrderService.java#L45): Kafka send without timeout → thread starvation (`.get(5s)`)
- [StreamController](08_kafka_streams/app/src/main/java/com/labs/kafkastreams/controller/StreamController.java#L53): Kafka send without timeout → thread starvation (`.get(5s)`)
- [OrderSagaOrchestrator](05_saga_pattern/app/src/main/java/com/labs/saga/saga/OrderSagaOrchestrator.java#L59): 6 `@KafkaListener` without `@RetryableTopic` → silent message loss
- [docker-compose.yml](docker-compose.yml): Zookeeper deprecated → migrated to KRaft mode
- [OrderController](04_outbox_kafka/app/src/main/java/com/labs/outbox/controller/OrderController.java#L40) + [SagaController](05_saga_pattern/app/src/main/java/com/labs/saga/controller/SagaController.java#L45): MDC logging added for observability
- [OrderController](04_outbox_kafka/app/src/main/java/com/labs/outbox/controller/OrderController.java#L34) + [SagaController](05_saga_pattern/app/src/main/java/com/labs/saga/controller/SagaController.java#L40): Log injection via MDC sanitized (CWE-117)

---

## Real Benchmark Results

Every figure below comes from a versioned results file, linked in each section.
Labs 01 and 07 were re-run on 2026-10-03 with the repo's own `run-benchmark.sh`; their results record environment, date and commit.

### Lab 01 — Virtual Threads

Median (min – max) over 5 runs per mode, 50 VUs, 200 tasks × 100 ms per request — [results](01_virtual_threads/benchmark/results/summary.md).

| Metric | Platform Threads (pool = 20) | Virtual Threads |
|--------|:----------------:|:---------------:|
| Throughput | 36.6 req/s (36.4 – 37.1) | **204.2 req/s** (201.4 – 205.0) — 5.6× |
| p50 latency | 1,008 ms | **101 ms** |
| p99 latency | 1,047 ms (1,017 – 1,251) | **108 ms** (105 – 111) |

Virtual threads eliminate the pool-size bottleneck under I/O-bound concurrency.
No reactive programming required.

### Lab 02 — Resilience4j

Source: [results](02_resilience/benchmark/summary.md).

| Metric | Value |
|--------|-------|
| Circuit OPEN duration | 27 s |
| Calls rejected while OPEN (`not_permitted`) | 90,680 |
| Recovery (OPEN → CLOSED) | automatic, ~5 s after the failure cleared |

Bulkhead + CircuitBreaker + Retry chain measured end-to-end under injected failure rate.

### Lab 03 — Redis Rate Limiter

Source: [results](03_rate_limiter/benchmark/results/summary.md).

| Metric | Value |
|--------|-------|
| Sustained throughput | 460 req/s |
| p99 latency | 4.55 ms |
| Redis outage response | HTTP 503 in < 1 ms |

Distributed token bucket — correct under multiple app instances sharing the same Redis.

### Lab 04 — Transactional Outbox

Source: [results](04_outbox_kafka/benchmark/results/summary.md).

| Metric | Value |
|--------|-------|
| Throughput | 163.8 req/s |
| Data loss under Kafka kill | zero |
| Drain rate (tuned poller) | 88.9 → 150.8 events/s (+70%) |

A silent data-loss bug (order created, event never enqueued) was found and fixed via chaos testing.
The outbox pattern prevents the dual-write race condition at the DB transaction boundary.

Verification available: [java-vibe-guard --verify VIBE-001](https://github.com/Joaquinriosheredia/java-vibe-guard)

### Lab 05 — Saga Pattern

Source: [results](05_saga_pattern/benchmark/results/summary.md).

| Metric | Value |
|--------|-------|
| Orders stuck in `STARTED` | 71.8% |
| Race condition demonstrated | dual-write without saga |
| HTTP response during failure | 202 Accepted, 0% HTTP errors (silent failure) |

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

| Metric | Sequential scan | Partial index |
|--------|:------:|:-----:|
| Query latency | 4.23 ms (1.08 – 34.59) | **1.76 ms** (0.82 – 6.67) |
| Ratio of medians | — | **2.4×** |

100K-row table, 5% PENDING rows, partial index on `occurred_at` WHERE `status = 'PENDING'`.
The index path ran as a bitmap scan plus sort (no `ANALYZE` after seeding); see the results file.

### Lab 08 — Kafka Streams

Source: [results](08_kafka_streams/benchmark/results/summary.md).

| Metric | Baseline | Post-Recovery |
|--------|:--------:|:-------------:|
| Throughput | **46.8 rps** | **109.7 rps** |
| p99 latency | **4 ms** | **4 ms** |
| Error rate | **0%** | **0%** |
| Consumer lag | **0** | **0** |
| Recovery time | — | **< 200 ms** (changelog replay) |

Critical finding: `actuator/health` reports `UP` and Streams state shows `RUNNING` even when Kafka is unreachable — standard health probes are false positives.
During Kafka outage: `kafkaTemplate.send().get()` blocks HTTP threads synchronously; 60% of requests failed (24/40) with a 15 s client timeout.
`at_least_once` guarantee with no duplicates on clean restart; lag=0 immediately after broker recovery.

### Lab 09 — Docker Optimization

**Not reproduced.** Neither committed Dockerfile builds from a clean checkout: `Dockerfile.naive` copies
`target/*.jar`, which the lab's `.dockerignore` excludes, and `Dockerfile` fails in `dependency:go-offline`
on `com.labs:labs-common:0.0.1-SNAPSHOT`, which is in no repository. No size, startup or rebuild figure is published until it does.

| Property | Naive Image | Optimized Image |
|--------|:-----------:|:---------------:|
| Runtime base | `eclipse-temurin:21-jdk` | `eclipse-temurin:21-jre-alpine` |
| Runs as | root | non-root (`appuser`) |

### Lab 10 — Kubernetes HPA

Source: [results](10_kubernetes_autoscaling/benchmark/results/summary.md).

- CPU con 50 VUs: 110m (threshold: 350m) — HPA nunca disparó
- 0% error rate con 1 solo pod durante toda la prueba
- Antipattern confirmado: CPU-based HPA es inservible con Virtual Threads
- Recovery real: 25s hasta readinessProbe (no 5s hasta Running)
- Solución: custom metrics HPA con lab_active_requests_gauge

---

## What Each Lab Demonstrates

### 01 · Virtual Threads
Java 21 Project Loom. Under I/O-bound load, 5.6× the throughput of a 20-thread platform pool
(204 vs 37 req/s, medians of 5 runs); p99 drops from 1,047 ms to 108 ms.
No reactive programming needed.

### 02 · Resilience
Resilience4j 2.x: composable `Bulkhead → CircuitBreaker → Retry`.
Inject failure rate via REST API, watch circuit transition `CLOSED → OPEN → HALF_OPEN → CLOSED`.
All state visible in `/actuator/health` and Prometheus.

### 03 · Rate Limiter
Distributed token bucket backed by Redis. Each API client gets an isolated bucket.
Works correctly with multiple app instances behind a load balancer.
Returns `Retry-After` and `X-RateLimit-Remaining` headers.

### 04 · Transactional Outbox
Order + outbox event written in one DB transaction.
Kill Kafka mid-flight: orders are created without errors, events queue in the outbox.
When Kafka recovers the poller drains the backlog — zero data loss.

Verification available: [java-vibe-guard --verify VIBE-001](https://github.com/Joaquinriosheredia/java-vibe-guard)

### 05 · Saga Pattern
Choreography-based saga over Kafka topics.
Inject inventory failure: watch `STARTED → PAYMENT_APPROVED → INVENTORY_FAILED → COMPENSATED`.
Full state machine visible in a single DB query.

### 06 · Redis vs Kafka
Side-by-side benchmark: Redis Pub/Sub (2,227 msg/s, ephemeral) vs Kafka (47,619 msg/s async, durable).
During a broker crash both lose what is sent (200/200); Kafka replays what was committed before it, Redis has nothing to replay.
The throughput gap is an API asymmetry, not a speed claim — see benchmark results for full analysis.

### 07 · PostgreSQL Tuning
100K rows, 5% PENDING. Sequential scan: 4.23 ms. Partial index: 1.76 ms. 2.4× (medians of 10 runs).
`EXPLAIN (ANALYZE, BUFFERS)` via `/api/v1/postgres/explain`.

### 08 · Kafka Streams
Tumbling 60-second window counting orders per user. 46.8 rps baseline, p99=4ms, lag=0.
Kafka broker killed mid-load: 60% error rate (24/40), health check false-positive (reports UP while Kafka is down).
Recovery to RUNNING in < 200ms via changelog topic replay — no duplicates, lag=0 immediately after restart.

### 09 · Docker Optimization
Naive image: full JDK, single fat-JAR layer, runs as root.
Optimized image: multi-stage build, layered JAR, JRE on Alpine, non-root (`appuser`), `-XX:MaxRAMPercentage=75.0`.
Benchmark not reproduced (see above).

### 10 · Kubernetes Autoscaling
HPA v2 on custom Prometheus metric `lab_active_requests_gauge`.
CPU stays low under virtual threads — CPU-only HPA would never trigger.
Graceful shutdown: in-flight requests complete before pod terminates.

---

## Running All Tests

```bash
make test-all
```

Or per lab:
```bash
cd 01_virtual_threads && ./mvnw verify
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
docker compose up -d  # starts shared prometheus + grafana
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

Every benchmark figure in this README links to a versioned results file in `<lab>/benchmark/`. Lab 09 has none and publishes no figures. Every architectural decision is documented in the ADRs.

---

## License

MIT
