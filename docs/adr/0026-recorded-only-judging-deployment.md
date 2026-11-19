# ADR-0026: Recorded-only judging deployment on a Hugging Face Space

| Field | Value |
| --- | --- |
| Status | Accepted; not deployed (the owner's Hugging Face account cannot create Docker Spaces; see ADR-0027) |
| Date | 2026-11-19 |
| Deciders | Project owner (chose the Space, the single container, bge-m3 in Ollama and the raised daily budget), Claude (author) |
| Related | ADR-0001 (stack), ADR-0003, ADR-0006 (data flow), ADR-0008 (cost), ADR-0021; rules R-01, R-02, R-03, R-07, R-12; PRD FR-85 (reduced), NFR-04; architecture §16 |

## Context

Submission is today (19 November 2026). Judges need a public HTTPS address with one-click demo login
and the recorded sample analysis. FR-85 asks for `docker-compose.prod.yml` on a cloud VM with Caddy,
n8n, backups and resource limits; there is no VM, no domain and no payment card for one. The owner
chose a free Hugging Face Space (Docker SDK: one container, 2 vCPU, 16 GB RAM, no persistent disk,
one public port, a `*.hf.space` address with HTTPS).

The deployment must not make a model call: the demo replays recorded answers (`fake-models`), so no
API key is needed on the server (R-12, ADR-0008). The recorded `risk-analysis` requests carry the
sections that retrieval returned, and recordings are found by the SHA-256 of the rendered request
(`FakeChatPort`). Retrieval therefore needs the real bge-m3 embeddings (profile `local-embeddings`),
which come from Ollama. A fake embedder would return other sections and every sample would fail with
`not-recorded`.

## Decision

1. **One container, five processes**, supervised by `deploy/hf/entrypoint.sh` under `tini`: PostgreSQL
   17 with pgvector, Ollama (bge-m3 only, baked into the image), the backend, Gotenberg (memo PDF)
   and nginx. If one of them exits the script exits and the Space restarts the container. Only nginx
   listens on the public port (`app_port: 7860`); everything else is bound to `127.0.0.1`.
2. **Profiles `demo,fake-models,local-embeddings`, never `prod`.** `StartupGuards` still refuses
   `prod` together with `demo` or `fake-models`, and is not changed. The deployment is a
   recorded-only judging site, not a production one; this is the documented choice. The one `prod`
   setting that matters, HSTS, is switched on with `SHROMIKAI_AUTH_HSTS=true`; cookies are `Secure`
   by default. No API key is set and the entrypoint unsets any that is present. No rule R-01 to R-12
   changes.
3. **No Ollama chat model.** Ollama serves `bge-m3` only (local, no cloud call). The local PII step,
   the obligations, the memo narrative and the baseline are the scripted stand-ins of `DemoModels`
   (`scripted-demo-standin`), as in the local demo. Results say `recorded`; the Space card, the docs
   and `known-gaps.md` say so.
4. **Statute corpus from a dump, restored at start.** `scripts/export-corpus.sh` writes a `pg_dump`
   archive with the full schema and Flyway history and the rows of six allowlisted tables only
   (`law_act`, `law_section` with embeddings, `section_edge`, `law_term`, `corpus_release`,
   `flyway_schema_history`). Every other table is exported empty, by an allowlist so that a table
   added later is empty by default, and the script refuses an archive with data elsewhere. The
   archive holds public statute text and no user, token, audit, analysis, usage or key row. It is
   3.6 MB, below the Hub's 10 MB limit for plain files, but binary, so it goes through Git LFS like
   the other binary files of the Space (`scripts/build-hf-space.sh` writes `.gitattributes` and
   refuses an uncovered binary); it is git-ignored in this repository and shipped only to the Space. The entrypoint restores it when `law_act` is empty, which is every
   start because the Space has no persistent disk. This ships the exact corpus release the
   recordings were made with; a scrape on the server could differ.
5. **Secrets from Space secrets**, never printed: `DEMO_PASSWORD` (required, 12+ characters),
   `JWT_SECRET` and `DB_PASSWORD` (optional; a random value per start when absent, which is safe
   because the database is empty at every start). The database accepts connections on loopback only.
6. **Single origin.** nginx serves the built frontend and proxies `/api`, `/v3/api-docs`,
   `/swagger-ui` and `/actuator/health` to the backend, so no CORS is needed (`CORS_ORIGINS` is set
   to the Space origin anyway) and the `SameSite=Strict` refresh cookie keeps working. The page must
   be opened at the direct `*.hf.space` address: inside the embedded view on huggingface.co the
   cookie is third-party and is not sent.
7. **Social image.** `index.html` uses a `%SITE_URL%` placeholder that the Vite build fills from the
   environment (empty in development and CI, so those builds are unchanged). The image build sets
   a token that the entrypoint replaces with `https://$SPACE_HOST` at start, so the Space address
   need not be known when the image is built.
8. **Daily budget raised, like `scripts/demo-backend.sh`.** `SHROMIKAI_BUDGET_DAILYUSD=1000000`:
   recorded answers are priced at their original cost and would reach the US$50 cap although nothing
   is spent. No key exists on the server, so no real spend is possible. The shipped default and the
   `prod` profile keep US$50.
9. **Privacy of the container.** nginx writes no access log and keeps a request body in memory below
   the 11 MB buffer; PostgreSQL logs no statement text; Gotenberg works in `/dev/shm` when it exists;
   the JVM does not dump the heap. Uploads of other documents get no analysis (no recording exists);
   the Space card says only the sample is recorded.
10. **Keep-alive.** `.github/workflows/keepalive.yml` requests `/api/v1/meta` every 10 minutes (no
    secret; the address is the repository variable `SPACE_URL`), so the free Space is not put to
    sleep after 48 hours without a visit.

## Consequences

- Cost: none (free Space, free GitHub Actions minutes). Start takes about a minute: PostgreSQL
  initialisation and restore, Ollama load, JVM start. nginx answers a self-reloading page meanwhile.
- Everything is lost at every restart (accounts are re-seeded, analyses are gone): acceptable for a
  recorded demo, wrong for production. Any real deployment still needs FR-85 as written.
- Not in this deployment: upload analysis of other documents, photo OCR (no Tesseract in the
  image), the MCP server, n8n, Prometheus, backups, resource limits, Dependabot and the OWASP check
  (the rest of FR-85 to FR-88 stays open; `known-gaps.md`).
- The image is amd64 on the Hub (built there from the Dockerfile, which sets no platform). It was
  built and tested natively (arm64) on the developer's machine only; no x86 image was built locally; embeddings of the Space's CPU may differ in the last digits from those of the recording
  machine. A near-tie in retrieval could then change the sections of a request and make the sample
  fail with `not-recorded`. Smoke check 4 of `scripts/smoke-deploy.sh` is the test, and it must be
  run against the live Space.
- The build installs software from public registries (Docker Hub images, PostgreSQL's apt
  repository, Maven Central, npm, the Ollama registry); versions of the images are pinned, apt and
  npm follow the lock file or the distribution.

## Alternatives

- **VPS with Docker Compose and Caddy** (the plan of the same day): needs an account, a card and a
  domain; rejected by the owner's constraints, and the better choice for a real deployment.
- **Frontend on Vercel or Netlify, backend elsewhere:** two origins, so the `SameSite=Strict`
  refresh cookie is never sent; fixing it means weakening the cookie.
- **A fake embedder instead of Ollama:** retrieval returns other sections, so the recordings do not
  replay (their keys include the retrieved sections); would need new recordings, which is out of
  scope and costs money.
- **Several containers:** the Space runs exactly one.

## Verification

- `scripts/smoke-deploy.sh <url>` (checks 1 to 7 of the plan: meta, health, demo login and cookie
  flags, sample analysis with the `not-recorded` check, memo PDF, pages and absolute `og:image`,
  HTTPS redirect and HSTS) was run against the arm64 container on the developer's machine (all
  checks passed; check 7 needs an https address and was skipped); its output is summarised in the
  dev-log. It must be run again against the live Space, where check 4 is the first x86 test.
- `FR-85`: frontend test `siteUrl.test.ts` pins the placeholders in `index.html`.
- `export-corpus.sh` fails if the archive carries a row of a non-allowlisted table.
