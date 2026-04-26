#!/usr/bin/env bash
# Lab 08 — Chaos: Kafka broker restart during stream processing
#
# Expected: Kafka Streams auto-recovers, replays from last committed offset.
# State store is restored from changelog topics.

set -euo pipefail
echo "================================================"
echo " Lab 08 — Chaos: Kafka Broker Restart"
echo "================================================"
echo "[1] Stopping Kafka..."
docker stop lab08-kafka 2>/dev/null || echo "Stop manually"
sleep 5
echo "[2] Restarting Kafka..."
docker start lab08-kafka 2>/dev/null || echo "Start manually"
echo "[3] Kafka Streams will reconnect automatically."
echo "Check stream status: curl http://localhost:8087/api/v1/streams/status"
echo "Expected: state transitions RUNNING → REBALANCING → RUNNING"
