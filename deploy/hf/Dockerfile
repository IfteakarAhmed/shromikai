# ShromikAI recorded-demo deployment: ONE container for a Hugging Face Space (Docker SDK).
# PostgreSQL+pgvector, Ollama (bge-m3 embeddings only), the backend (profiles demo, fake-models,
# local-embeddings), Gotenberg (memo PDF) and nginx (frontend + /api proxy) on one public port.
# No cloud call is possible: no API key is read and the fake-models profile has no cloud adapter.
# See docs/adr/0026-recorded-only-judging-deployment.md. Built from the staging directory made by
# scripts/build-hf-space.sh (the Space repository root).

# (the frontend and the jar do not depend on the CPU, so they build on the host's own platform)
# ---- frontend: static build; SITE_URL is a token the container fills with the Space host -------
FROM --platform=$BUILDPLATFORM node:24-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
COPY docs /src/docs
COPY eval/reports /src/eval/reports
ENV SITE_URL=__SITE_URL__
RUN npx tsc --noEmit && npx vite build

# ---- backend: boot jar and the recorded model answers (kept out of the jar) -------------------
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS backend
WORKDIR /src/backend
COPY backend/gradlew backend/settings.gradle backend/build.gradle backend/gradle.properties ./
COPY backend/gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY backend/src ./src
COPY eval/contracts/synthetic /src/eval/contracts/synthetic
RUN ./gradlew --no-daemon bootJar prepareDemoFixtures -x test

# ---- Ollama without the GPU libraries (the CUDA and Jetson folders are about 3 GB) -------------
FROM ollama/ollama:0.40.16 AS ollama
RUN cd /usr/lib/ollama && rm -rf cuda* rocm* *jetpack* vulkan* 2>/dev/null; ls /usr/lib/ollama

# ---- runtime ----------------------------------------------------------------------------------
FROM debian:bookworm-slim AS runtime
ENV DEBIAN_FRONTEND=noninteractive LANG=C.UTF-8
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl gnupg tini nginx procps openssl \
      chromium qpdf libimage-exiftool-perl \
      fonts-noto-core fonts-noto-ui-core fonts-lohit-beng-bengali fonts-liberation fonts-dejavu-core \
 && install -d /usr/share/keyrings \
 && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/keyrings/pgdg.gpg \
 && echo "deb [signed-by=/usr/share/keyrings/pgdg.gpg] https://apt.postgresql.org/pub/repos/apt bookworm-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
 && apt-get update && apt-get install -y --no-install-recommends postgresql-17 postgresql-17-pgvector \
 && rm -rf /var/lib/apt/lists/* /etc/nginx/sites-enabled/* /etc/nginx/conf.d/*

# Java 21 runtime, Ollama and Gotenberg, copied from their pinned images.
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
COPY --from=ollama /usr/bin/ollama /usr/bin/ollama
COPY --from=ollama /usr/lib/ollama /usr/lib/ollama
COPY --from=gotenberg/gotenberg:8.21.1 /usr/bin/gotenberg /usr/bin/pdfcpu /usr/bin/
# Gotenberg refuses to start without a path for every module. The memo is HTML to PDF through
# Chromium, so LibreOffice, its unoconverter and pdftk are stubs that fail if ever called.
RUN printf '#!/bin/sh\necho "not available in this image" >&2\nexit 1\n' > /usr/local/bin/unavailable \
 && chmod +x /usr/local/bin/unavailable
ENV CHROMIUM_BIN_PATH=/usr/bin/chromium QPDF_BIN_PATH=/usr/bin/qpdf EXIFTOOL_BIN_PATH=/usr/bin/exiftool \
    PDFCPU_BIN_PATH=/usr/bin/pdfcpu PDFTK_BIN_PATH=/usr/local/bin/unavailable \
    LIBREOFFICE_BIN_PATH=/usr/local/bin/unavailable UNOCONVERTER_BIN_PATH=/usr/local/bin/unavailable
ENV JAVA_HOME=/opt/java/openjdk PATH=/opt/java/openjdk/bin:/usr/lib/postgresql/17/bin:$PATH
RUN java -version

# The Space runs as uid 1000 and has no persistent disk: everything lives under /home/user.
RUN useradd -m -u 1000 user \
 && mkdir -p /opt/ollama-models /app/data /app/demo-fixtures /app/backend \
 && chown -R user:user /opt/ollama-models /app /home/user

# bge-m3 is baked into the image: nothing is downloaded when the Space starts.
USER user
ENV OLLAMA_MODELS=/opt/ollama-models OLLAMA_HOST=127.0.0.1:11434
RUN set -e; \
    (ollama serve > /tmp/ollama-build.log 2>&1 &); \
    for i in $(seq 1 60); do curl -fs http://127.0.0.1:11434/api/version >/dev/null && break; sleep 1; done; \
    ollama pull bge-m3; \
    ollama list; \
    pkill ollama || true; \
    test -d /opt/ollama-models/manifests

COPY --from=backend --chown=user:user /src/backend/build/libs/shromikai-backend.jar /app/backend/app.jar
COPY --from=backend --chown=user:user /src/backend/build/demo-fixtures /app/demo-fixtures
COPY --from=frontend --chown=user:user /src/frontend/dist /app/www-template
COPY --chown=user:user data/rules /app/data/rules
COPY --chown=user:user data/mappings /app/data/mappings
COPY --chown=user:user data/packs /app/data/packs
COPY --chown=user:user data/privacy /app/data/privacy
COPY --chown=user:user corpus/corpus.dump /app/corpus.dump
COPY --chown=user:user deploy/nginx.conf.template deploy/entrypoint.sh /app/
RUN chmod +x /app/entrypoint.sh

WORKDIR /app/backend
EXPOSE 7860
ENTRYPOINT ["/usr/bin/tini", "--", "/app/entrypoint.sh"]
