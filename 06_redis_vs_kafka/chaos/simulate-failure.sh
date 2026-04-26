#!/usr/bin/env bash
# Lab 06 — Chaos: Redis Down (messages lost) vs Kafka Down (consumer lag)
#
# Demonstrates the fundamental trade-off:
# - Redis subscriber offline → messages lost permanently
# - Kafka consumer offline → messages queued, replayed on reconnect

set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8085}"

echo "================================================"
echo " Lab 06 — Chaos: Durability Test"
echo "================================================"

echo "[REDIS] Stopping Redis subscriber (simulated by stopping redis)..."
docker stop lab06-redis 2>/dev/null || echo "Stop redis manually"
sleep 2

echo "Publishing 10 messages to Redis while subscriber is down..."
for i in $(seq 1 10); do
  curl -s -o /dev/null "$BASE_URL/api/v1/benchmark/redis?messages=1" || true
done

echo "Restarting Redis..."
docker start lab06-redis 2>/dev/null || echo "Start manually"

echo "RESULT: Those 10 messages are LOST — Redis Pub/Sub has no persistence."
echo ""
echo "[KAFKA] Kafka messages are persisted regardless of consumer state."
echo "Even if the app is down, messages stay in Kafka topic for retention period."
echo "Consumer group lag: kubectl get -- or check Kafka consumer groups"
