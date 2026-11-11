#!/usr/bin/env bash
# Shows, step by step, the citation verifier catching a planted fake statute section (FR-46).
#
# Offline and free: the models are scripted stand-ins (the output says so), the statute text comes
# from the corpus in the local database (docker compose up -d db; ./gradlew ingestLaws once).
# Database settings come from DB_URL, DB_USER and DB_PASSWORD (defaults of docker-compose.yml).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  export JAVA_HOME
fi

cd "$root/backend"
exec ./gradlew --quiet --console=plain demoFakeCitation
