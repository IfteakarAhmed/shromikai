#!/usr/bin/env bash
# Runs the backend the way the demo and the end-to-end tests need it: seeded demo accounts, the
# recorded model answers of the synthetic sample contract, local embeddings (Ollama) and the
# development database. No cloud call is possible: the API keys are removed from the environment and
# the fake-models profile has no cloud adapter.
#
#   ./scripts/demo-backend.sh            # http://localhost:8080
#
# Needs: docker compose up -d db ollama (scripts/dev-up.sh), and DEMO_PASSWORD and JWT_SECRET in .env.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
unset ANTHROPIC_API_KEY GEMINI_API_KEY GEMINI_MODEL

export SPRING_PROFILES_ACTIVE="demo,fake-models,local-embeddings"
export SHROMIKAI_FAKE_MODELS_DEFAULT_CASE="syn-en-02-worker"
cd backend
exec ./gradlew bootRun "$@"
