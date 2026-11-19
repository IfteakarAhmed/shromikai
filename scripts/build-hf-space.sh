#!/usr/bin/env bash
# Assembles the Hugging Face Space repository (ADR-0026) in a clean staging directory: the
# Dockerfile, the Space card, the sources the image is built from, the public statute corpus
# archive and nothing else (no git history, no .env, no tests, no local data).
#
#   ./scripts/export-corpus.sh              # once: writes deploy/hf/corpus.dump
#   ./scripts/build-hf-space.sh [dir]       # default: build/hf-space
#   docker build --platform linux/amd64 -t shromikai-space build/hf-space
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-build/hf-space}"

[ -f deploy/hf/corpus.dump ] || { echo "run scripts/export-corpus.sh first" >&2; exit 1; }
rm -rf "$OUT"
mkdir -p "$OUT/deploy" "$OUT/corpus" "$OUT/backend/src/test/resources/fixtures" "$OUT/data" \
  "$OUT/eval/contracts/synthetic" "$OUT/eval/reports" "$OUT/docs/prompts" "$OUT/docs/spec"

cp deploy/hf/Dockerfile deploy/hf/README.md "$OUT/"
cp deploy/hf/entrypoint.sh deploy/hf/nginx.conf.template "$OUT/deploy/"
cp deploy/hf/corpus.dump "$OUT/corpus/corpus.dump"

cp backend/gradlew backend/settings.gradle backend/build.gradle backend/gradle.properties "$OUT/backend/"
cp -R backend/gradle "$OUT/backend/gradle"
cp -R backend/src/main "$OUT/backend/src/main"
cp -R backend/src/test/resources/fixtures/llm "$OUT/backend/src/test/resources/fixtures/llm"

rsync -a --exclude node_modules --exclude dist --exclude coverage --exclude playwright-report \
  --exclude test-results --exclude '*.tsbuildinfo' --exclude e2e --exclude .vite frontend/ "$OUT/frontend/"

for d in rules mappings packs privacy; do cp -R "data/$d" "$OUT/data/$d"; done
cp eval/contracts/synthetic/syn-en-02.md eval/contracts/synthetic/syn-en-02.entities.yaml \
  "$OUT/eval/contracts/synthetic/"
cp eval/reports/*.json "$OUT/eval/reports/"
cp docs/*.md "$OUT/docs/"
cp docs/prompts/*.md "$OUT/docs/prompts/"
cp docs/spec/architecture.md "$OUT/docs/spec/"

# The Hub refuses plain binary files in a push, so every binary type goes through Git LFS (the
# corpus archive is 3 MB, below the 10 MB limit, but is binary too).
cat > "$OUT/.gitattributes" <<'ATTR'
*.dump filter=lfs diff=lfs merge=lfs -text
*.jar filter=lfs diff=lfs merge=lfs -text
*.jpg filter=lfs diff=lfs merge=lfs -text
*.jpeg filter=lfs diff=lfs merge=lfs -text
*.png filter=lfs diff=lfs merge=lfs -text
*.webp filter=lfs diff=lfs merge=lfs -text
*.gif filter=lfs diff=lfs merge=lfs -text
*.ico filter=lfs diff=lfs merge=lfs -text
*.woff filter=lfs diff=lfs merge=lfs -text
*.woff2 filter=lfs diff=lfs merge=lfs -text
*.pdf filter=lfs diff=lfs merge=lfs -text
ATTR
uncovered=""
while IFS= read -r f; do
  if ! grep -Iq . "$OUT/$f" 2>/dev/null; then
    case "$f" in
      *.dump | *.jar | *.jpg | *.jpeg | *.png | *.webp | *.gif | *.ico | *.woff | *.woff2 | *.pdf) ;;
      *) uncovered="$uncovered $f" ;;
    esac
  fi
done < <(cd "$OUT" && find . -type f ! -name .gitattributes)
if [ -n "$uncovered" ]; then
  echo "refusing: binary files not covered by .gitattributes:$uncovered" >&2
  exit 1
fi

# Guards: no secret or contract file may enter the Space.
if find "$OUT" \( -name '.env' -o -name '.env.*' -o -name '*.contract.pdf' \) | grep -q .; then
  echo "refusing: a secret or contract file is in the staging directory" >&2
  exit 1
fi
echo "staged $OUT ($(du -sh "$OUT" | cut -f1)); corpus archive $(du -h "$OUT/corpus/corpus.dump" | cut -f1)"
