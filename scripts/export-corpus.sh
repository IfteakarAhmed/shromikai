#!/usr/bin/env bash
# Exports the statute corpus of the local development database to a pg_dump archive for the
# recorded-demo deployment (ADR-0026). The archive holds the full schema (including the Flyway
# history, so the backend finds every migration applied) and the DATA of the public statute tables
# only. Every other table is exported empty: users, tokens, audit, traces, analyses, usage, API keys
# and playbooks never leave the machine. The table list is an allowlist, so a table added by a later
# migration is empty by default.
#
#   ./scripts/export-corpus.sh [output-file]      # default: deploy/hf/corpus.dump (git-ignored)
#
# Needs the compose database (scripts/dev-up.sh) and pg_dump/pg_restore of version 17 or newer.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
OUT="${1:-deploy/hf/corpus.dump}"
HOST="${DB_HOST:-localhost}"
PORT="${DB_PORT:-5432}"
USER_NAME="${DB_USER:-shromikai}"
export PGPASSWORD="${DB_PASSWORD:-shromikai}"
DB="${DB_NAME:-shromikai}"

# Public statute tables whose rows are exported (V1 and V4 migrations) plus the migration history.
ALLOW=(law_act law_section section_edge law_term corpus_release flyway_schema_history)

TABLES="$(psql -h "$HOST" -p "$PORT" -U "$USER_NAME" -d "$DB" -At \
  -c "select tablename from pg_tables where schemaname='public' order by 1")"
EXCLUDE=()
for table in $TABLES; do
  keep=0
  for allowed in "${ALLOW[@]}"; do
    [ "$table" = "$allowed" ] && keep=1
  done
  [ "$keep" -eq 0 ] && EXCLUDE+=("--exclude-table-data=public.${table}")
done

mkdir -p "$(dirname "$OUT")"
pg_dump -h "$HOST" -p "$PORT" -U "$USER_NAME" -d "$DB" -Fc -Z 9 --no-owner --no-acl \
  "${EXCLUDE[@]}" -f "$OUT"

# Guard: the archive may carry rows of the allowlisted tables only.
DATA_TABLES="$(pg_restore -l "$OUT" | awk '/TABLE DATA/ {print $7}' | sort | tr '\n' ' ')"
for table in $DATA_TABLES; do
  ok=0
  for allowed in "${ALLOW[@]}"; do
    [ "$table" = "$allowed" ] && ok=1
  done
  if [ "$ok" -eq 0 ]; then
    echo "refusing: $table has data in the archive" >&2
    rm -f "$OUT"
    exit 1
  fi
done
echo "wrote $OUT ($(du -h "$OUT" | cut -f1)); data only in: $DATA_TABLES"
