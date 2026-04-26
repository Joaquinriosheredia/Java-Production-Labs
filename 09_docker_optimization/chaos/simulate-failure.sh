#!/usr/bin/env bash
# Lab 09 — Chaos: Memory Limit Violation (OOM)
#
# Demonstrates what happens when JVM doesn't respect container memory limits.
# Without -XX:+UseContainerSupport, JVM reads host memory and allocates beyond cgroup limit.
# Expected: OOM kill from container runtime.

set -euo pipefail
echo "================================================"
echo " Lab 09 — Chaos: Container Memory Limit"
echo "================================================"

echo "[1] Starting container WITHOUT container-aware JVM flags (naive)..."
docker run -d --name lab09-oom-test \
  --memory=128m \
  -e JAVA_TOOL_OPTIONS="-Xmx512m" \
  -p 8090:8088 \
  lab09-naive 2>/dev/null || echo "Build lab09-naive first: docker build -f docker/Dockerfile.naive -t lab09-naive ."

sleep 5

echo ""
echo "[2] Container status (may be OOMKilled):"
docker inspect lab09-oom-test --format='State: {{.State.Status}}, OOMKilled: {{.State.OOMKilled}}' 2>/dev/null || echo "Container not running"

echo ""
echo "[3] Optimized container respects memory limit with -XX:MaxRAMPercentage=75.0"
echo "    128MB limit → heap = 96MB (75%), no OOM"

docker rm -f lab09-oom-test 2>/dev/null || true
echo ""
echo "Chaos scenario complete."
