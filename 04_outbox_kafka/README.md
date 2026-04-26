# Lab 04 — Transactional Outbox Pattern

## Problem

An order service must persist an order in PostgreSQL AND notify inventory/shipping via Kafka.
The dual-write problem: if Kafka is down after the DB commit, the event is lost.
If the app crashes mid-flight, the event is lost. The customer is charged but nothing ships.

**How do you guarantee event delivery without distributed transactions?**

---

## Architecture

```mermaid
sequenceDiagram
    participant Client
    participant App
    participant PostgreSQL
    participant Poller
    participant Kafka

    Client->>App: POST /orders
    App->>PostgreSQL: BEGIN TXN
    App->>PostgreSQL: INSERT orders
    App->>PostgreSQL: INSERT outbox_events (PENDING)
    App->>PostgreSQL: COMMIT
    App-->>Client: 201 Created

    loop Every 1s
        Poller->>PostgreSQL: SELECT WHERE status=PENDING
        Poller->>Kafka: publish(OrderCreated)
        Poller->>PostgreSQL: UPDATE status=PUBLISHED
    end
```

---

## Key Guarantee

**If the DB transaction commits, the event WILL be delivered.**
Kafka downtime = events queue in the outbox. When Kafka recovers, the poller drains the backlog.

---

## How to Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

# Create an order
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"customer-1","amount":99.99}'

# Check outbox stats
curl http://localhost:8083/api/v1/orders/outbox/stats
```

---

## How to Break It

```bash
bash chaos/simulate-failure.sh
```

Stops Kafka, creates 5 orders, then restarts Kafka. Shows events queue and drain.

---

## How to Measure

```bash
bash benchmark/run-benchmark.sh
```

Key metric: `SELECT COUNT(*) FROM outbox_events WHERE status='PENDING'` — this is your delivery lag gauge.

---

## See Also

- [ADR-0001](docs/adr/ADR-0001.md): Why Outbox over dual-write or Debezium
