# Contributing — Quality Playbook

Every lab in this repository must meet the **production standard** described below.
Pull requests that don't comply will be rejected at CI level.

---

## Quality Gate — Mandatory per Lab

| Artifact       | File(s)                                   | Must contain                            |
|----------------|-------------------------------------------|-----------------------------------------|
| README         | `<lab>/README.md`                         | Problem, Architecture, How to run, How to break it, Benchmark results |
| ADR            | `<lab>/docs/adr/ADR-0001.md`              | Context, Decision, Alternatives, Trade-offs |
| Benchmark      | `<lab>/benchmark/README.md` + script      | Reproducible metric (latency/throughput/memory) |
| Chaos script   | `<lab>/chaos/simulate-failure.sh`         | At least one failure mode               |
| Tests          | `<lab>/app/src/test/`                     | Unit + integration, Testcontainers (no infra mocks) |
| Docker Compose | `<lab>/docker/docker-compose.yml`         | Runnable with `docker compose up -d`    |

---

## Commit Convention

```
<type>(<lab>): <short description>

Types: feat | fix | test | bench | chaos | docs | ci | refactor
Examples:
  feat(01_virtual_threads): add async endpoint comparison
  bench(02_resilience): add k6 circuit breaker scenario
  chaos(03_rate_limiter): add latency injection script
```

---

## Adding a New Lab

1. Copy `01_virtual_threads` as a template skeleton
2. Fill every mandatory artifact (no empty placeholders)
3. Add lab entry to root `README.md` table
4. Add ADR entry to `docs/adr/README.md`
5. Add `make lab-XX` and `make benchmark-XX` targets to root `Makefile`
6. CI validates presence of all artifacts — it will fail if any are missing

---

## Code Standards

- **Java 21** — virtual threads, records, sealed interfaces where applicable
- **Spring Boot 3.x** — no deprecated APIs
- **No infra mocks** — Testcontainers for PostgreSQL, Redis, Kafka, etc.
- **Structured logs** — JSON via Logback, include `traceId` and `labName` MDC fields
- **Metrics** — expose at `/actuator/prometheus`, label with `lab` tag
- **No `System.out.println`** — use SLF4J
- **No hardcoded ports in tests** — use dynamic port allocation

---

## Benchmark Standards

- k6 scripts must include: ramp-up, steady state, ramp-down stages
- JMH benchmarks must run with at least 3 forks and 5 warmup iterations
- Results must be committed in `benchmark/results/` (CSV or JSON)
- README must include a baseline and an optimized result for comparison

---

## Chaos / Failure Mode Standards

- Every chaos script must be idempotent (safe to run multiple times)
- Must print what it's doing and how to recover
- Must document the expected observable effect (metric, log, response code)
