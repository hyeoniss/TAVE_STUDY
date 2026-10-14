#!/usr/bin/env sh
set -eu

mkdir -p k6-results

docker compose --profile benchmark up -d --build --wait

BASE_URL=http://localhost:8081 \
RESULT_PATH=k6-results/no-cache.json \
k6 run k6/cache-load-test.js

BASE_URL=http://localhost:8080 \
RESULT_PATH=k6-results/cache.json \
k6 run k6/cache-load-test.js

echo "Results written to k6-results/no-cache.json and k6-results/cache.json"
