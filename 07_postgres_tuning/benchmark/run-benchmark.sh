#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8086}"
ROWS="${ROWS:-100000}"

echo "=== Lab 07 — PostgreSQL Tuning Benchmark ==="
echo "Seeding $ROWS rows..."
curl -s -X POST "$BASE_URL/api/v1/postgres/seed?rows=$ROWS" > /dev/null

echo "Comparing seq scan vs index scan..."
curl -s "$BASE_URL/api/v1/postgres/compare?limit=100"
