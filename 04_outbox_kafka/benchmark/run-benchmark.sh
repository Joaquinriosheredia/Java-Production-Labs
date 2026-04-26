#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8083}"
echo "=== Lab 04 — Outbox Kafka Benchmark ==="
if ! command -v k6 &>/dev/null; then echo "ERROR: k6 required"; exit 1; fi
mkdir -p results
k6 run --env BASE_URL="$BASE_URL" --out json=results/outbox.json load-test.js
echo "Outbox stats after load: $(curl -s $BASE_URL/api/v1/orders/outbox/stats)"
