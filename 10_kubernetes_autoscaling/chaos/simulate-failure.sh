#!/usr/bin/env bash
# Lab 10 — Chaos: Readiness Probe Failure
#
# Sets the readiness probe to fail, which causes Kubernetes to:
# 1. Remove the pod from the Service endpoints (stop sending traffic)
# 2. HPA won't count the pod in scaling decisions
#
# Expected observable effect:
#   - kubectl get endpoints lab10-autoscaling → pod removed
#   - Traffic routes only to healthy pods
#   - After recovery, pod re-added to endpoints

set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8089}"

echo "================================================"
echo " Lab 10 — Chaos: Readiness Probe Failure"
echo "================================================"

echo "[1] Simulating high queue depth (triggers HPA scale-up signal)..."
curl -s -X POST "$BASE_URL/api/v1/chaos/queue-depth?depth=50"
echo ""

echo "[2] Current metrics:"
curl -s "$BASE_URL/api/v1/metrics/snapshot"
echo ""

echo "[3] In Kubernetes, check HPA:"
echo "  kubectl get hpa lab10-hpa"
echo "  kubectl describe hpa lab10-hpa"
echo ""

echo "[4] Resetting queue depth..."
curl -s -X POST "$BASE_URL/api/v1/chaos/queue-depth?depth=0"
echo ""
echo "Chaos scenario complete."
