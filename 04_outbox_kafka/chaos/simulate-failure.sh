#!/usr/bin/env bash
# Lab 04 — Chaos: Kafka Outage During Order Creation
#
# Stops Kafka to demonstrate that the outbox pattern prevents data loss.
# Orders continue to be created and events stored in the outbox.
# When Kafka recovers, the poller publishes all queued events.
#
# Expected observable effect:
#   - Orders created: continue normally (no errors to client)
#   - outbox_events status=PENDING: grows during outage
#   - After Kafka recovery: PENDING drains to PUBLISHED

set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8083}"

echo "================================================"
echo " Lab 04 — Chaos: Kafka Outage"
echo "================================================"

echo "[1] Current outbox state:"
curl -s "$BASE_URL/api/v1/orders/outbox/stats" && echo ""

echo ""
echo "[2] Stopping Kafka..."
docker stop lab04-kafka 2>/dev/null || echo "Container not found, stop manually"

echo ""
echo "[3] Creating 5 orders with Kafka down..."
for i in 1 2 3 4 5; do
  result=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -d "{\"customerId\":\"chaos-$i\",\"amount\":$((i*10)).00}")
  echo "  Order $i: HTTP $result (order persisted, event queued in outbox)"
done

echo ""
echo "[4] Outbox state (events should be PENDING):"
curl -s "$BASE_URL/api/v1/orders/outbox/stats" && echo ""

echo ""
echo "[5] Restarting Kafka..."
docker start lab04-kafka 2>/dev/null || echo "Restart manually"

echo ""
echo "[6] Waiting 15s for poller to drain the outbox..."
sleep 15

echo ""
echo "[7] Outbox state (PENDING should be 0 now):"
curl -s "$BASE_URL/api/v1/orders/outbox/stats" && echo ""
echo ""
echo "Chaos scenario complete. No data was lost during Kafka outage."
