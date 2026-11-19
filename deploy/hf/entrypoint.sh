#!/usr/bin/env bash
# Starts the whole recorded-demo stack inside one container (ADR-0026) and supervises it: if a
# service the demo cannot work without exits, the script exits too, and the Space restarts it.
# Secrets come from the environment (Space secrets) and are never printed:
#   DEMO_PASSWORD  required, at least 12 characters (judge accounts)
#   JWT_SECRET     optional, at least 32 bytes; a random one is made per start when absent
#   DB_PASSWORD    optional; a random one is made per start when absent
set -euo pipefail
umask 077

log() { printf '{"msg":"%s"}\n' "$*"; }
die() { printf '{"msg":"%s","level":"ERROR"}\n' "$*" >&2; exit 1; }

# ---- configuration (no value below is ever echoed) ---------------------------------------------
[ -n "${DEMO_PASSWORD:-}" ] || die "DEMO_PASSWORD is not set (Space secret)"
[ "${#DEMO_PASSWORD}" -ge 12 ] || die "DEMO_PASSWORD must have at least 12 characters"
if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET="$(openssl rand -base64 48)"
  log "JWT_SECRET not set: using a random secret for this start (sessions end when the Space restarts)"
fi
[ "$(printf %s "$JWT_SECRET" | wc -c)" -ge 32 ] || die "JWT_SECRET must have at least 32 bytes"
if [ -z "${DB_PASSWORD:-}" ]; then
  DB_PASSWORD="$(openssl rand -hex 24)"
fi
export DEMO_PASSWORD JWT_SECRET DB_PASSWORD

PORT="${PORT:-7860}"
HOME_DIR="${HOME:-/home/user}"
PGDATA="$HOME_DIR/pgdata"
WWW="$HOME_DIR/www"
RUN_DIR="$HOME_DIR/run"
SITE_URL_VALUE="${SITE_URL:-}"
if [ -n "${SPACE_HOST:-}" ]; then SITE_URL_VALUE="https://${SPACE_HOST}"; fi
[ -n "$SITE_URL_VALUE" ] || SITE_URL_VALUE="http://localhost:${PORT}"
mkdir -p "$RUN_DIR" /tmp/nginx

PIDS=()
cleanup() { for pid in "${PIDS[@]:-}"; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT TERM INT

# ---- frontend: fill the public origin into the built pages --------------------------------------
rm -rf "$WWW" && cp -r /app/www-template "$WWW"
sed -i "s#__SITE_URL__#${SITE_URL_VALUE}#g" "$WWW"/index.html
sed "s#@PORT@#${PORT}#; s#@WWW@#${WWW}#" /app/nginx.conf.template > "$RUN_DIR/nginx.conf"

# nginx first: a visitor who arrives while the backend starts gets a self-reloading page.
nginx -c "$RUN_DIR/nginx.conf" -g 'daemon off;' &
PIDS+=($!)

# ---- PostgreSQL + pgvector, corpus restored on first start (idempotent) -------------------------
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  printf %s "$DB_PASSWORD" > "$RUN_DIR/pwfile"
  initdb -D "$PGDATA" -U shromikai --auth-local=trust --auth-host=scram-sha-256 \
    --pwfile="$RUN_DIR/pwfile" -E UTF8 --locale=C.UTF-8 >/dev/null
  rm -f "$RUN_DIR/pwfile"
fi
# No statement text in the server log (R-02): errors are terse.
postgres -D "$PGDATA" -c listen_addresses=127.0.0.1 -c unix_socket_directories="$RUN_DIR" \
  -c shared_buffers=256MB -c fsync=off -c synchronous_commit=off -c full_page_writes=off \
  -c log_min_error_statement=panic -c log_error_verbosity=terse -c log_statement=none \
  -c max_connections=60 &
PIDS+=($!)
for _ in $(seq 1 60); do pg_isready -q -h "$RUN_DIR" -U shromikai -d postgres && break; sleep 0.5; done
pg_isready -q -h "$RUN_DIR" -U shromikai -d postgres || die "PostgreSQL did not start"

PSQL=(psql -q -h "$RUN_DIR" -U shromikai -v ON_ERROR_STOP=1)
if [ "$("${PSQL[@]}" -At -d postgres -c "select count(*) from pg_database where datname='shromikai'")" = "0" ]; then
  "${PSQL[@]}" -d postgres -c "create database shromikai" >/dev/null
fi
"${PSQL[@]}" -d shromikai -c "create extension if not exists vector" -c "create extension if not exists citext" >/dev/null
LOADED=0
if [ "$("${PSQL[@]}" -At -d shromikai -c "select to_regclass('public.law_act') is not null")" = "t" ]; then
  LOADED="$("${PSQL[@]}" -At -d shromikai -c "select count(*) from law_act")"
fi
if [ "$LOADED" = "0" ]; then
  log "restoring the statute corpus (public statute text only)"
  pg_restore -h "$RUN_DIR" -U shromikai -d shromikai --no-owner --no-acl --no-comments \
    --exit-on-error /app/corpus.dump || die "corpus restore failed"
fi
log "corpus ready: $("${PSQL[@]}" -At -d shromikai -c "select count(*) from law_section") sections"

# ---- Ollama: bge-m3 embeddings only, loaded now so the first analysis does not wait ------------
OLLAMA_KEEP_ALIVE=-1 OLLAMA_NUM_PARALLEL=2 ollama serve >/dev/null 2>&1 &
PIDS+=($!)
for _ in $(seq 1 60); do curl -fs http://127.0.0.1:11434/api/version >/dev/null && break; sleep 0.5; done
curl -fs http://127.0.0.1:11434/api/embed -d '{"model":"bge-m3","input":"warm up","keep_alive":-1}' >/dev/null \
  || die "Ollama did not load bge-m3"

# ---- Gotenberg: memo PDF; its work files go to memory-backed storage when there is any ---------
GOT_TMP=/dev/shm/gotenberg
mkdir -p "$GOT_TMP" 2>/dev/null || { GOT_TMP=/tmp/gotenberg; mkdir -p "$GOT_TMP"; }
TMPDIR="$GOT_TMP" gotenberg --api-port=3000 --api-bind-ip=127.0.0.1 \
  --libreoffice-disable-routes=true --chromium-disable-javascript=true \
  --chromium-deny-list="^(?!data:|file://${GOT_TMP}/).*" >/dev/null 2>&1 &
PIDS+=($!)

# ---- backend: recorded model answers, seeded judge accounts, no API keys ------------------------
unset ANTHROPIC_API_KEY GEMINI_API_KEY GEMINI_MODEL
export SPRING_PROFILES_ACTIVE="demo,fake-models,local-embeddings"
export SHROMIKAI_FAKE_MODELS_DEFAULT_CASE="syn-en-02-worker"
export DB_URL="jdbc:postgresql://127.0.0.1:5432/shromikai" DB_USER=shromikai
export OLLAMA_URL="http://127.0.0.1:11434" GOTENBERG_URL="http://127.0.0.1:3000"
export CORS_ORIGINS="$SITE_URL_VALUE"
export SERVER_FORWARD_HEADERS_STRATEGY=native      # real client IP behind the Space proxy and nginx
export SHROMIKAI_AUTH_HSTS=true
# The recorded answers are priced at their original cost, so a long run would reach the daily budget
# of US$50 although nothing is spent; no API key exists here, so no real spend is possible.
export SHROMIKAI_BUDGET_DAILYUSD=1000000
export SERVER_ADDRESS=127.0.0.1
java -Xms256m -Xmx1g -XX:-HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError \
  -Dfile.encoding=UTF-8 -Dloader.path=/app/demo-fixtures \
  -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher &
BACKEND_PID=$!
PIDS+=($BACKEND_PID)

for _ in $(seq 1 180); do
  curl -fs http://127.0.0.1:8080/actuator/health >/dev/null 2>&1 && break
  kill -0 "$BACKEND_PID" 2>/dev/null || die "backend exited during start"
  sleep 1
done
log "ShromikAI is up on port ${PORT} (${SITE_URL_VALUE})"

# Exit as soon as any service dies, so the Space restarts the whole container.
wait -n "${PIDS[@]}"
die "a service exited; stopping so the Space restarts"
