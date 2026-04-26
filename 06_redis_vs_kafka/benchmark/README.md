# Benchmark — Lab 06: Redis vs Kafka

## Metric

**Publish throughput (messages/second)** and **end-to-end latency** for Redis Pub/Sub vs Kafka.

---

## Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

# Compare both
curl "http://localhost:8085/api/v1/benchmark/compare?messages=1000"
```

---

## Expected Results (local, single-node)

| Backend | Messages | Publish Time | Throughput |
|---------|----------|-------------|-----------|
| Redis Pub/Sub | 1000 | ~50ms | ~20,000 msg/s |
| Kafka (acks=all) | 1000 | ~2000ms | ~500 msg/s |

**Key insight**: Redis is ~40× faster for publish but provides zero durability.
Kafka is slower but guarantees delivery even if the consumer is offline.

---

## Trade-off Matrix

| Use Case | Winner |
|----------|--------|
| Cache invalidation | Redis |
| Live chat notifications | Redis |
| Order events (money) | Kafka |
| Audit log | Kafka |
| Fan-out to 100 consumers | Redis (simpler) |
| Replay past 7 days of events | Kafka |
