#!/usr/bin/env bash
set -euo pipefail

echo "==> Starting Lab 01 — Virtual Threads"
echo "==> Infrastructure: none required (self-contained)"
echo ""

cd "$(dirname "$0")/.."

echo "==> Building and running application..."
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.tracePinnedThreads=full"
