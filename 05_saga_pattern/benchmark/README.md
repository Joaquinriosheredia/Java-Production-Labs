# Benchmark — Lab 05: Saga Pattern

## Metric

**End-to-end saga completion time** and **compensation rate** under injected failures.

---

## Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

bash benchmark/run-benchmark.sh
```

---

## Key Metrics

- `lab_saga_completed_total` — sagas that reached COMPLETED
- `lab_saga_compensated_total` — sagas that required compensation
- End-to-end time: time from `POST /saga/orders` to `sagaStatus=COMPLETED`

---

## Expected Results

| Scenario | Avg Completion Time | Compensation Rate |
|----------|--------------------|--------------------|
| Happy path (no failures) | ~200ms | 0% |
| 50% inventory failure | ~300ms | ~50% |
| 100% payment failure | ~100ms (fast fail) | 100% |
