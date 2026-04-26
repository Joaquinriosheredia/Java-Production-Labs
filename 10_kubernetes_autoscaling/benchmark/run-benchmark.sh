#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8089}"
echo "=== Lab 10 — Kubernetes Autoscaling Benchmark ==="
if ! command -v k6 &>/dev/null; then echo "ERROR: k6 required"; exit 1; fi
mkdir -p results
k6 run --env BASE_URL="$BASE_URL" --out json=results/k8s-autoscaling.json load-test.js
