#!/usr/bin/env bash
# Lab 02 — Chaos: Circuit Breaker Trip
#
# Sets failure rate to 100%, fires enough calls to open the circuit,
# then restores service and observes recovery through HALF_OPEN → CLOSED.
#
# Expected observable effects:
#   - First 10 calls: RuntimeException (retried 3 times each → slow)
#   - After call 10: circuit OPEN → immediate fallback (<5ms)
#   - After 10s: circuit HALF_OPEN → 3 probe calls succeed → CLOSED

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"

echo "================================================"
echo " Lab 02 — Chaos: Circuit Breaker Trip"
echo "================================================"

echo "[1] Setting failure rate to 100%..."
curl -s -X POST "$BASE_URL/api/v1/admin/failure-rate?percent=100" | python3 -m json.tool

echo ""
echo "[2] Firing 15 requests to trip the circuit..."
for i in $(seq 1 15); do
  result=$(curl -s "$BASE_URL/api/v1/call?requestId=chaos-$i")
  fallback=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin)['fallback'])" 2>/dev/null || echo "?")
  echo "  req-$i: fallback=$fallback"
done

echo ""
echo "[3] Circuit should be OPEN now. Check:"
curl -s "$BASE_URL/actuator/health" | python3 -m json.tool 2>/dev/null | grep -A3 circuitBreakers || \
  echo "  (Install python3 for pretty print, or check http://localhost:8081/actuator/health)"

echo ""
echo "[4] Restoring service (failure rate = 0%)..."
curl -s -X POST "$BASE_URL/api/v1/admin/failure-rate?percent=0" | python3 -m json.tool

echo ""
echo "[5] Wait 10s for circuit to transition to HALF_OPEN..."
sleep 10

echo ""
echo "[6] Firing 3 probe calls (should succeed and close circuit)..."
for i in 1 2 3; do
  result=$(curl -s "$BASE_URL/api/v1/call?requestId=probe-$i")
  echo "  probe-$i: $result"
done

echo ""
echo "Chaos scenario complete."
echo "Circuit should be CLOSED. Verify: curl $BASE_URL/actuator/health"
