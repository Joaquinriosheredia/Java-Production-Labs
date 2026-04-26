# Architecture Decision Records — Global Index

This index tracks all ADRs across labs. Each lab maintains its own ADRs under `<lab>/docs/adr/`.

---

## Index

| Lab | ADR | Title | Status |
|-----|-----|-------|--------|
| 01_virtual_threads | [ADR-0001](../../01_virtual_threads/docs/adr/ADR-0001.md) | Use Virtual Threads over Platform Threads for I/O-bound workloads | Accepted |
| 02_resilience | [ADR-0001](../../02_resilience/docs/adr/ADR-0001.md) | Use Resilience4j over Hystrix for circuit breaking | Accepted |
| 03_rate_limiter | [ADR-0001](../../03_rate_limiter/docs/adr/ADR-0001.md) | Use Redis token bucket over in-memory rate limiting | Accepted |
| 04_outbox_kafka | [ADR-0001](../../04_outbox_kafka/docs/adr/ADR-0001.md) | Transactional Outbox Pattern for at-least-once delivery | Accepted |
| 05_saga_pattern | [ADR-0001](../../05_saga_pattern/docs/adr/ADR-0001.md) | Choreography-based Saga over Orchestration for decoupling | Accepted |
| 06_redis_vs_kafka | [ADR-0001](../../06_redis_vs_kafka/docs/adr/ADR-0001.md) | Redis Pub/Sub for ephemeral events, Kafka for durable streams | Accepted |
| 07_postgres_tuning | [ADR-0001](../../07_postgres_tuning/docs/adr/ADR-0001.md) | Partial indexes and query rewriting for hot-path optimization | Accepted |
| 08_kafka_streams | [ADR-0001](../../08_kafka_streams/docs/adr/ADR-0001.md) | Kafka Streams over Flink for in-process stream processing | Accepted |
| 09_docker_optimization | [ADR-0001](../../09_docker_optimization/docs/adr/ADR-0001.md) | Multi-stage build with layered JARs and non-root user | Accepted |
| 10_kubernetes_autoscaling | [ADR-0001](../../10_kubernetes_autoscaling/docs/adr/ADR-0001.md) | HPA on custom Prometheus metrics over CPU-only scaling | Accepted |

---

## ADR Template

```markdown
# ADR-XXXX — Title

**Date**: YYYY-MM-DD
**Status**: Draft | Accepted | Deprecated | Superseded by ADR-XXXX

## Context
What problem are we solving? What forces are at play?

## Decision
What did we decide?

## Alternatives Considered
| Alternative | Reason rejected |
|-------------|-----------------|
| ...         | ...             |

## Trade-offs
- Pro: ...
- Con: ...

## Consequences
What changes as a result of this decision?
```
