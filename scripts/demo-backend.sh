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

# The recorded answers are priced at their original cost, so a long test run would reach the daily
# budget of the shipped configuration (US$50, FR-62) although nothing is spent: this script removes
# the cloud API keys above, so no real spend is possible. Only this script raises the limit; the
# shipped default and the prod profile keep US$50 (DailyBudgetDefaultTest).
export SHROMIKAI_BUDGET_DAILYUSD=1000000

# E2E=1 (the end-to-end tests set it) lifts the per-IP login limits and the guest daily quotas that
# protect a public deployment, because a test run signs in far more often than a visitor does.
if [ "${E2E:-}" = "1" ]; then
  export SHROMIKAI_AUTH_RATE_LIMIT_PER_IP_PER_MINUTE=100000
  export SHROMIKAI_AUTH_RATE_LIMIT_PER_USER_PER_MINUTE=100000
  export SHROMIKAI_QUOTAS_GUEST_PER_IP_PER_DAY=100000
  export SHROMIKAI_QUOTAS_GUEST_PER_DEVICE_PER_DAY=100000
fi

# DEMO_RULES=off loads no rule file, so the earlier recordings (made before the rules were active)
# replay: the result then has UNCERTAIN findings and the "no active rule applied" notice. The
# default is on. Only this script reads the flag (DemoRulesFlagTest); production never does.
if [ "${DEMO_RULES:-on}" = "off" ]; then
  export SHROMIKAI_RULES_FILES=
fi

export SPRING_PROFILES_ACTIVE="demo,fake-models,local-embeddings"
export SHROMIKAI_FAKE_MODELS_DEFAULT_CASE="syn-en-02-worker"
cd backend
exec ./gradlew bootRun "$@"
