# Benchmark — Lab 04: Transactional Outbox

## Metric

**Event delivery lag** (time from order creation to Kafka publish) and **throughput** under load.

---

## Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

bash benchmark/run-benchmark.sh
```

---

## Key Metrics

- `lab_orders_created_total` — order creation rate
- `lab_outbox_published_total` — Kafka publish rate
- `lab_outbox_failed_total` — publish failures
- `SELECT COUNT(*) FROM outbox_events WHERE status='PENDING'` — delivery lag gauge

---

## Expected Results

| Scenario | Order/s | Avg Delivery Lag |
|----------|---------|-----------------|
| Normal load | 100/s | ~1s (1 poll cycle) |
| Kafka down | 100/s | N/A (events queued in outbox) |
| Kafka recovered | - | Backlog cleared in seconds |
