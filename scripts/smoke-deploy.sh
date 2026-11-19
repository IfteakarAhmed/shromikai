#!/usr/bin/env bash
# Smoke checks of the recorded-demo deployment (ADR-0026). Prints PASS/FAIL per check and never
# prints a secret. The demo password is read from the DEMO_PASSWORD environment variable only to
# test /api/v1/auth/login; the checks use the one-click demo login otherwise.
#
#   ./scripts/smoke-deploy.sh https://<owner>-<space>.hf.space
#   ./scripts/smoke-deploy.sh http://localhost:7860          # the local container
set -uo pipefail
BASE="${1:?usage: smoke-deploy.sh <base-url>}"
BASE="${BASE%/}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fails=0
pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1"; fails=$((fails + 1)); }
json() { python3 -I -c "import json,sys; d=json.load(open(sys.argv[1])); print(eval(sys.argv[2], {'d': d}))" "$1" "$2" 2>/dev/null; }

# 1. meta ------------------------------------------------------------------------------------------
code=$(curl -sS -m 30 -o "$TMP/meta.json" -w '%{http_code}' "$BASE/api/v1/meta")
if [ "$code" = 200 ] && [ "$(json "$TMP/meta.json" "d.get('recordedDemo') is not None")" = True ]; then
  pass "1 GET /api/v1/meta 200, recordedDemo present"
else fail "1 GET /api/v1/meta (HTTP $code)"; fi

# 2. health ----------------------------------------------------------------------------------------
for path in /healthz /actuator/health; do
  code=$(curl -sS -m 30 -o "$TMP/h.json" -w '%{http_code}' "$BASE$path")
  if [ "$code" = 200 ] && [ "$(json "$TMP/h.json" "d['status']")" = UP ]; then pass "2 $path UP"
  else fail "2 $path (HTTP $code)"; fi
done

# 3. one-click demo login, cookie flags ------------------------------------------------------------
code=$(curl -sS -m 30 -X POST -D "$TMP/login.h" -o "$TMP/login.json" -w '%{http_code}' "$BASE/api/v1/auth/demo/worker")
TOKEN="$(json "$TMP/login.json" "d['accessToken']")"
if [ "$code" = 200 ] && [ -n "$TOKEN" ]; then pass "3 demo login 200, access token returned"; else fail "3 demo login (HTTP $code)"; fi
cookie="$(grep -i '^set-cookie:' "$TMP/login.h" | head -1)"
case "$BASE" in https://*) need='secure' ;; *) need='httponly' ;; esac
if printf %s "$cookie" | grep -qi 'httponly' && printf %s "$cookie" | grep -qi 'samesite=strict' \
   && printf %s "$cookie" | grep -qi "$need"; then
  pass "3 refresh cookie: HttpOnly, SameSite=Strict$(printf %s "$cookie" | grep -qi 'secure' && printf ', Secure')"
else fail "3 refresh cookie flags"; fi

# 4. recorded sample analysis; no 'not-recorded' (embedding drift check) ---------------------------
OPTIONS='{"side":"WORKER","outputLanguage":"bn","clientKind":"DESKTOP_WEB","sample":true,"playbookId":null,"profile":null,"workerCategory":null}'
code=$(curl -sS -m 60 -X POST -H "Authorization: Bearer $TOKEN" -F "options=$OPTIONS;type=application/json" \
  -o "$TMP/acc.json" -w '%{http_code}' "$BASE/api/v1/analyses")
ID="$(json "$TMP/acc.json" "d['analysisId']")"
if [ -z "$ID" ]; then fail "4 start analysis (HTTP $code)"; else
  result=""
  for _ in $(seq 1 90); do
    code=$(curl -sS -m 30 -H "Authorization: Bearer $TOKEN" -o "$TMP/res.json" -w '%{http_code}' "$BASE/api/v1/analyses/$ID")
    if [ "$code" = 200 ] && [ "$(json "$TMP/res.json" "'findings' in d")" = True ]; then result=ok; break; fi
    if [ "$code" = 200 ] && [ "$(json "$TMP/res.json" "d.get('status')")" = FAILED ]; then result=failed; break; fi
    sleep 2
  done
  if [ "$result" = ok ]; then
    n=$(json "$TMP/res.json" "len(d['findings'])")
    ver=$(json "$TMP/res.json" "all(f['verificationStatus']=='VERIFIED' for f in d['findings'] if f.get('citations'))")
    rec=$(json "$TMP/res.json" "d['provenance']['recorded']")
    sample=$(json "$TMP/res.json" "d['input']['sample']")
    if [ "$n" -ge 3 ] && [ "$ver" = True ] && [ "$rec" = True ] && [ "$sample" = True ]; then
      pass "4 sample analysis: $n findings, citations verified, provenance.recorded=true"
    else fail "4 sample analysis shape (findings=$n verified=$ver recorded=$rec sample=$sample)"; fi
    cp "$TMP/res.json" "$TMP/result.json"
  else
    fail "4 sample analysis did not finish: $(json "$TMP/res.json" "d.get('problem') or d" | cut -c1-300)"
  fi
fi
if grep -q 'not-recorded' "$TMP/res.json" 2>/dev/null; then fail "4 'not-recorded' found: embeddings differ from the recording machine"; \
else pass "4 no 'not-recorded' problem (retrieval matches the recordings)"; fi

# 5. memo PDF through Gotenberg --------------------------------------------------------------------
if [ -f "$TMP/result.json" ]; then
  python3 -I - "$TMP/result.json" "$TMP/memo-request.json" <<'PY'
import json, sys
result = json.load(open(sys.argv[1]))
envelope = result.pop("envelope")
body = {"envelope": {"payload": result, "keyId": envelope["keyId"], "algorithm": envelope["algorithm"],
        "expiresAt": envelope["expiresAt"], "signature": envelope["signature"]}, "format": "PDF"}
json.dump(body, open(sys.argv[2], "w"))
PY
  code=$(curl -sS -m 90 -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    --data-binary "@$TMP/memo-request.json" -D "$TMP/memo.h" -o "$TMP/memo.pdf" -w '%{http_code}' "$BASE/api/v1/memos/render")
  if [ "$code" = 200 ] && [ "$(head -c 4 "$TMP/memo.pdf")" = "%PDF" ]; then
    pass "5 memo PDF rendered ($(wc -c < "$TMP/memo.pdf" | tr -d ' ') bytes)"
  else fail "5 memo PDF (HTTP $code)"; fi
else fail "5 memo PDF skipped: no analysis result"; fi

# 6. pages and the absolute social image -----------------------------------------------------------
for page in / /docs /docs/evaluation /docs/pitch /demo-user /swagger-ui/index.html; do
  code=$(curl -sS -m 30 -o "$TMP/page.html" -w '%{http_code}' "$BASE$page")
  if [ "$code" = 200 ]; then pass "6 GET $page 200"; else fail "6 GET $page (HTTP $code)"; fi
done
curl -sS -m 30 -o "$TMP/index.html" "$BASE/"
img="$(grep -o 'property="og:image" content="[^"]*"' "$TMP/index.html" | sed 's/.*content="//; s/"$//')"
case "$img" in
  http://*|https://*) pass "6 og:image is absolute: $img" ;;
  *) fail "6 og:image is not absolute: '$img'" ;;
esac
grep -q '__SITE_URL__\|%SITE_URL%' "$TMP/index.html" && fail "6 index.html still has a SITE_URL token"

# 7. HTTPS redirect and HSTS (https targets only) --------------------------------------------------
case "$BASE" in
  https://*)
    host="${BASE#https://}"
    loc="$(curl -sS -m 30 -o /dev/null -D - "http://$host/" 2>/dev/null | grep -i '^location:' | tr -d '\r')"
    if printf %s "$loc" | grep -qi 'https://'; then pass "7 http redirects to https"; else fail "7 http does not redirect to https"; fi
    if curl -sSI -m 30 "$BASE/api/v1/meta" | grep -qi '^strict-transport-security:'; then pass "7 HSTS header on the API"; else fail "7 no HSTS header"; fi ;;
  *) printf 'SKIP  7 HTTPS redirect and HSTS (not an https URL)\n' ;;
esac

[ "$fails" -eq 0 ] && { echo "ALL CHECKS PASSED"; exit 0; } || { echo "$fails CHECK(S) FAILED"; exit 1; }
