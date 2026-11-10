#!/usr/bin/env bash
# Starts PostgreSQL (pgvector) and Ollama for local development, waits until both are healthy,
# then pulls the two local models (ADR-0003, ADR-0005). Safe to run repeatedly.
set -euo pipefail

cd "$(dirname "$0")/.."

MODELS=("bge-m3" "qwen2.5:7b-instruct")

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running. Start Docker Desktop and try again." >&2
  exit 1
fi

echo "Starting db and ollama..."
docker compose up -d db ollama

wait_healthy() {
  local service=$1
  local id
  id=$(docker compose ps -q "$service")
  for _ in $(seq 1 60); do
    if [ "$(docker inspect -f '{{.State.Health.Status}}' "$id")" = "healthy" ]; then
      echo "$service is healthy"
      return 0
    fi
    sleep 2
  done
  echo "$service did not become healthy" >&2
  docker compose logs --tail 50 "$service" >&2
  exit 1
}

wait_healthy db
wait_healthy ollama

for model in "${MODELS[@]}"; do
  echo "Pulling $model (the first run downloads several GB)..."
  docker compose exec -T ollama ollama pull "$model"
done

echo
echo "Ready. Next:"
echo "  backend:  cd backend && ./gradlew bootRun   (or the IntelliJ run configuration)"
echo "  frontend: cd frontend && npm run dev"
