#!/usr/bin/env bash
# Lab 05 — Chaos: Saga Compensation Flow
#
# Injects inventory failure to trigger the compensation path.
# Expected: payment is approved then refunded, order ends as COMPENSATED.

set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8084}"

echo "================================================"
echo " Lab 05 — Chaos: Saga Compensation"
echo "================================================"

echo "[1] Enabling inventory failure simulation..."
curl -s -X POST "$BASE_URL/api/v1/saga/chaos/inventory-failure?enabled=true"
echo ""

echo ""
echo "[2] Creating order (should trigger compensation)..."
ORDER=$(curl -s -X POST "$BASE_URL/api/v1/saga/orders" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"chaos-customer","amount":99.99}')
ORDER_ID=$(echo "$ORDER" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
echo "Order ID: $ORDER_ID"

echo ""
echo "[3] Waiting 10s for compensation to complete..."
sleep 10

echo "[4] Final saga status:"
curl -s "$BASE_URL/api/v1/saga/orders/$ORDER_ID" | python3 -m json.tool 2>/dev/null || echo "Check manually"

echo ""
echo "[5] Disabling inventory failure..."
curl -s -X POST "$BASE_URL/api/v1/saga/chaos/inventory-failure?enabled=false"

echo ""
echo "Expected final status: COMPENSATED"
echo "Saga stats: $(curl -s $BASE_URL/api/v1/saga/stats)"
