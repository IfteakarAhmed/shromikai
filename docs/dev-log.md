# Development log

Key prompts given to AI tools while building ShromikAI, and what they produced. Updated at the end of each phase.

## 2026-11-09 — Planning

- **Prompt (to Claude):** Read the event guide and judging criteria; recommend the best challenge and a full project plan we can build with Claude's help.
- **Result:** Chose JusticeSphere AI → Legal Copilot. Project plan with five core features, verified-citation architecture, evaluation plan and judging-criteria mapping.
- **Prompt (to ChatGPT):** Review Claude's plan critically.
- **Result:** Kept scope to one use case (employment contracts), added test-case structure, abstention rule, law-version handling. MCP moved to the final round.

## 2026-11-10 — Phase 1: Foundation

- **Prompt (to Claude):** Set up the repository with a Gradle + YAML Spring Boot backend and a React + TypeScript frontend.
- **Result:** Gradle build, `application.yml`, Flyway schema (law sections with versions, section graph, trace, review queue), docker-compose (pgvector + Ollama), `/api/v1/meta` endpoint, frontend shell showing backend status.

## 2026-11-10 — Plan update from the submission form

- **Prompt (to Claude):** Here are screenshots of every tab of the submission form; adjust the project so it scores well and write master prompts we can give one by one.
- **Result:** Added login + judge test accounts, MCP server, local LLM (Qwen via Ollama) for PII, independent verifier model, rule engine, n8n automation and a live /docs page to the plan. Wrote AGENTS.md, docs/build-prompts.md (11 phase prompts) and docs/submission-map.md.

## 2026-11-10 — Master prompt playbook v1

- **Prompt (to Claude):** Re-read the full participants guide, the judging criteria and the submission-form screenshots; write world-class master prompts (with test cases in code) so we can build the whole project in fresh Claude sessions; deliver as a PDF.
- **Result:** `docs/playbook/master-prompt-playbook.md` + PDF: usage guide (Bangla), timeline and cut line, judging and form traceability matrix, 13 phase prompts P0–P12 in XML-structured form with named tests and acceptance checks, an independent reviewer prompt (R) and a fix prompt (F).

## 2026-11-10 — P0: Specification

- **Prompt (to Claude Code):** The P0 prompt from the master prompt playbook: act as principal engineer and product architect and write the specification layer (AGENTS.md, PRD, architecture, API, test strategy, evaluation, ADR-0001 to ADR-0005, team, traceability), documents only; first read the repository and propose the outline and the 10 most important design decisions for approval.
- **Result:** The team approved the outline and decisions. The specification was written, then rebased onto the playbook commits and aligned with the playbook (the spec adopts the playbook's phases, auth design, intake limits, retrieval parameters, API paths, eval layout and test names; the playbook was edited to v1.1 where it conflicted with approved decisions). Self-reviewed against the quality bar and judging criteria; tagged `spec-v1`. Phase report below.

## Phase report - P0

- **Summary:** Wrote specification v1 aligned with the master prompt playbook: `AGENTS.md`, a PRD with 89 functional requirements (grouped by playbook phase P1 to P12), 21 non-functional requirements and 14 KPIs, architecture, API, test-strategy and evaluation specs (26 metrics), 5 ADRs, team roles and a traceability matrix. Recorded 12 Phase 1 conflicts and 6 playbook corrections (architecture §18). No application code changed.
- **Files created or changed:**
  - Created: `AGENTS.md` (replaces the 25-line version from the plan commit), `CLAUDE.md` (replaced), `docs/spec/PRD.md`, `docs/spec/architecture.md`, `docs/spec/api.md`, `docs/spec/test-strategy.md`, `docs/spec/evaluation.md`, `docs/spec/traceability.md`, `docs/adr/0001-stack.md` to `docs/adr/0005-model-routing.md`, `docs/team.md`.
  - Changed: `README.md` (stack, spec and playbook links), `docs/architecture.md` (now a pointer), `docs/prompts/README.md` (prompt catalogue and standard), `docs/tooling-disclosure.md` (runtime models and what each receives), `docs/playbook/master-prompt-playbook.md` (v1.1 corrections), `docs/submission-map.md` (MCP tool count, agent-framework wording, pointer to traceability), `docs/dev-log.md` (this entry).
- **Key decisions and ADRs:**
  1. Verification is a hard gate: `VERIFIED` / `UNCERTAIN` / `REJECTED`, no `WEAK`; `PARTIAL` support becomes `UNCERTAIN`; only `verify` can construct `VerifiedCitation` (ADR-0002).
  2. The risk agent can cite only retrieved sections: per-request schema `enum` plus a `NOT_IN_CANDIDATES` existence check; a corrective loop rewrites the query and re-retrieves once (ADR-0002).
  3. Deterministic rule engine on typed facts; rules in reviewed YAML whose quotes are checked against the corpus at startup (ADR-0002, ADR-0004).
  4. Local-first intake: local OCR, Bijoy conversion, two-stage local PII redaction with `[TYPE_n]` placeholders, egress guard, fail closed (ADR-0003).
  5. "Contract text" defined as any span of 8 or more tokens or any PII value; none of it at rest; canary tests prove it (ADR-0003).
  6. Asynchronous jobs with SSE; redacted result in memory for at most 30 minutes (playbook value, was 15); memo PDF rendered by Gotenberg from an HMAC-signed envelope (ADR-0001, ADR-0003).
  7. Model routing by role, including query rewriting and the counter-offer agent; Gemini Flash as independent verifier and calibrated judge (ADR-0005).
  8. Claude through the official Anthropic Java SDK behind `ChatPort`; no sampling parameters; structured outputs and `effort` (ADR-0001, ADR-0005).
  9. Runtime prompts as immutable versioned files with linted front matter and `<contract_clause>` isolation (`AGENTS.md` §5).
  10. PostgreSQL only: one section = one embedding, full-text search, one-hop graph; jurisdiction packs as data (ADR-0004).
  - Adopted from the playbook: phases P0 to P12 with dates and the cut line; JWT auth with roles `WORKER`, `HR`, `ADMIN` and one-click demo access; intake limits (10 MB, 15 pages); retrieval parameters; API paths; MCP tool names; eval layout and runner; test names.
  - Added to the playbook: guest tokens so workers need no account, and `/demo-hr` so judges can see the HR journey.
  - On reflection, the MCP tool `analyze_employment_contract` does not conflict with an approved decision: it runs the same pipeline and privacy shield as `POST /api/v1/analyses` and stores nothing, so the spec adopts it with a no-echo rule (FR-66) instead of removing it.
- **Open questions for the team:**
  1. Who holds each role in `docs/team.md`? The event needs 3 to 5 named members; the third member (ideally a law student) is also needed as legal reviewer before rule review (P6) and label review (P10).
  2. Do we have a GPU host with at least 16 GB VRAM for the judging period? Without one, NFR-01 is met only in the CPU-only mode (120 s target).
  3. Will we have a Gemini API key? Without it, every support check runs on the same-family fallback and the LLM-as-judge cannot be used.
  4. Do the terms of use of bdlaws.minlaw.gov.bd and ILO NORMLEX allow automated fetching at one request per second? If not, P3 uses manual downloads with attribution.
  5. Who owns the Anthropic and Google accounts, and which data-retention settings are available? These go into `docs/tooling-disclosure.md` at P11.
  6. Hosting provider, domain and daily cost cap (default US$50) for the public demo?
  7. Is the cost target acceptable? The proposal estimated about US$0.30 per analysis; the itemised derivation (PRD §9.1) gives about US$0.41, so NFR-03 sets the median target at US$0.40.
  8. Can the NRB advisor arrange the customer interviews and letters of interest that test pricing hypotheses H-1 to H-4?
  9. Should the playbook PDF be regenerated from the v1.1 Markdown? The PDF still shows v1.0 wording (including the `WEAK` status).
- **Next phase prerequisites (P1):**
  1. Role owners named in `docs/team.md`.
  2. Exact versions chosen and pinned: latest Spring AI 1.x GA release for Spring Boot 3.5, Anthropic Java SDK, Ollama image tag.
  3. P1 scope as in the playbook P1 prompt plus the Phase 1 fixes C2, C3, C6, C8, C10 and C12 (architecture §18.1) and migration `V2__foundation.sql`.
  4. Developer machines: Docker with at least 16 GB memory; `scripts/dev-up.sh` pulls `bge-m3` and `qwen2.5:7b-instruct`.
  5. GitHub Actions enabled for the repository (CI badge in P1).

## 2026-11-10 — P0 review fixes: specification v1.1

- **Prompt (to Claude Code):** Apply the 14 fixes from the independent Phase 0 review (Prompt R verdict PASS WITH FIXES) to the specification only, re-check every document, and tag `spec-v1.1`.
- **Result:** Specification v1.1 and playbook v1.2. The signed envelope holds the re-identified result, and `MemoFromRealResponseIT` renders a memo from a real response. SSE uses a fetch-based client with a Bearer header, and tokens are never accepted in URLs. Free-text law search makes no cloud call, and MCP claims are redacted locally. The 8-token rule now also covers `claimed_claim`. Module-matrix violations are fixed without changing the matrix (`QuoteMatcher` in `shared.text`, the codec signs JSON trees, re-identification goes through `analysis`). The retrieval smoke check is a `retrieval` service. Latency and cost budgets are reconciled: parallel risk batches, counter-offers after verification, output caps, a stated output-speed assumption, NFR-01 split by input kind, and an estimate of about US$0.34 per analysis. Conflicts C13 to C17 are recorded. Metrics M-27 and M-28 are added, and the unassigned tests now have phases. The playbook disclaimer and wording are aligned with the spec, and the outdated PDF is deleted. Traceability gains team-bonus rows and a proof-test column. Three ADRs were added: ADR-0006 (data flow and retention), ADR-0007 (citation origins `MAPPING` and `EXTERNAL`) and ADR-0008 (latency and cost budget). Architecture §18.3 maps each fix to where it landed. No application code changed; `spec-v1` is kept and `spec-v1.1` added.
- **Open for the team:** output-speed assumptions in architecture §5.3 must be measured in P6; Gemini pricing and the exact Spring AI 1.1.x patch are pinned at P6 and P1.

## 2026-11-10 — P1: Foundation, quality gates and CI

- **Prompt (to Claude Code):** The P1 prompt from the master prompt playbook: audit the Phase 1 foundation (commit `7209544`) against the spec and bring it up to standard (build, profiles, quality gates, AI ports and fakes, problem documents, request IDs, log redaction, meta and health, compose, CI, README), tests first, then the standard phase report.
- **Result:** The plan (files, dependencies, test list and three decisions where the prompt and the spec disagreed) was approved as recommended: `promptLint` built in P1, a thin Gemini adapter in P1, and springdoc in P1 so the frontend types are generated. Report below.

## Phase report - P1

- **Summary:** The foundation now builds on Spring Boot 3.5.16 and Spring AI 1.1.8 with Claude through the Anthropic Java SDK, behind `ChatPort` and `EmbeddingPort` with deterministic fakes. `./gradlew check` enforces the module matrix, JUnit tags, the log-key allowlist, the prompt standard, the fixture rules, Spotless and JaCoCo. The React app is Bangla first with generated API types, and CI covers the backend, the frontend and fixtures. Phase 1 conflicts C2, C3, C6, C8, C10, C12, C15, C16 and C17 are fixed.
- **Files created or changed:**
  - **Backend build:** `build.gradle` (Boot 3.5.16, Spring AI BOM 1.1.8, Anthropic SDK 3.7.0; Spring AI Anthropic starter removed; test tasks `test` / `integrationTest` / `slowTest` / `liveTest` by tag; JaCoCo gates; `promptLint`, `checkFixtures`, `requirementsReport`), `settings.gradle` (foojay toolchain resolver), `gradle.properties`, `Dockerfile` (non-root, `-XX:-HeapDumpOnOutOfMemoryError`).
  - **Configuration:** `application.yml` with profiles `default`, `dev`, `test`, `fake-models`, `demo`, `prod`, `mcp`. It sets `spring.ai.model.chat: ollama` (C2), has no Claude sampling options (C3), uses a multipart threshold of 11 MB (C12), never pulls models at startup, caps Spring AI retries at 2, and keeps `shromikai.models.*`, providers and fake-model settings.
  - **Migration:** `V2__foundation.sql`. It adds `law_section.content_hash` and `parser_version`, plus `audit_event` with append-only triggers. Deletes are allowed only for rows older than 12 months, for the retention job.
  - **`shared` module:**
    - `SensitiveText`.
    - `config/` (`ShromikaiProperties`, `StartupGuards`): startup fails if `store-contracts` is true or `prod` and `demo` are active together.
    - `problem/` (`ProblemType` covering all api.md §7 codes in bn and en, `ProblemException`).
    - `web/RequestIdFilter`.
    - `logging/` (`LogKeys`, `LogRedactor`, `AllowlistJsonFormatter`).
    - `json/JsonSchemas`.
  - **`shared.ai` module:**
    - Ports and types: `ChatPort`, `EmbeddingPort`, `EgressGuard`, request and response types, model exceptions.
    - `internal/FailClosedEgressGuard`, which blocks every cloud call until P5, and `internal/Backoff`.
    - Cloud adapters: `anthropic/AnthropicChatAdapter` and `gemini/GeminiChatAdapter`.
    - Ollama: `ollama/` (chat and embedding adapters, health indicator `ollama`).
    - Fakes: `fake/` (`FakeChatPort`, `FakeEmbeddingPort`, failure modes).
    - Wiring: `AiConfiguration`.
  - **Other modules:**
    - `law`: `CorpusStatsService` and `law.internal.CorpusStatsRepository`.
    - `api`: `MetaController` and `Meta`. `api.internal` holds `WebConfig` (moved from `bd.shromikai.config`, all methods allowed, C6 and C10), `GlobalProblemHandler`, `ProblemErrorController`, `ProblemFactory` and `OpenApiConfig`.
    - `trace`: package placeholder.
    - `prompts/LOCK`.
  - **Backend tests and tooling (`src/test`):**
    - Test classes: 14 unit and 8 integration.
    - `testsupport/` (`@Requirement`, `@IntegrationTest`, the shared pgvector container, `BlockExternalHostsExtension`, tag samples).
    - `tooling/` (`PromptLint`, `FixtureScanner`, `RequirementsReport`).
    - Resources: test schemas, the `xx-echo` fixture, `fixtures/canaries.yml`.
  - **Frontend:**
    - Config: `package.json` (scripts `lint`, `typecheck`, `test`, `build`, `gen:api`, `check:bundle`), `eslint.config.js` (typescript-eslint strict), `.prettierrc.json`, `vite.config.ts` (Vitest with a 70 % statement gate), `tsconfig*.json`, `.nvmrc`, `.vscode/extensions.json`, `scripts/check-bundle-size.mjs`.
    - App: `index.html` (no font CDN), `src/App.tsx`, `src/main.tsx`, `src/fonts.ts` (self-hosted Hind Siliguri), `src/i18n/{bn,en}.json`.
    - API: `src/api/client.ts`, `src/api/openapi.json` (snapshot), `src/api/types.ts` (generated).
    - Tests: `src/test/` (MSW, setup), `App.test.tsx`, `i18n.test.ts`, `client.test.ts`.
    - Removed `src/api.ts`.
  - **Infrastructure:** `docker-compose.yml` (pinned `pgvector/pgvector:0.8.7-pg17` and `ollama/ollama:0.40.16`, health checks, named volumes, configurable `DB_PORT`; C8), `scripts/dev-up.sh`, `scripts/check-fixtures`, `.env.example` (every §12 variable, `ANTHROPIC_MODEL` removed; C16), `.gitignore` (`data/raw/`; C15).
  - **CI and docs:** `.github/workflows/ci.yml` (jobs `backend`, `frontend`, `fixtures`), README (CI badge, macOS quickstart for IntelliJ and VS Code), ADR-0001 (version log, springdoc moved to P1, Hind Siliguri row), api.md §7 and §11 (`method-not-allowed`, `not-acceptable`, `instance` format, OpenAPI from P1).
- **Tests added and passing:** 107 backend unit tests in 14 classes, 35 integration tests in 8 classes, and 14 frontend tests in 3 files. All pass.
  - **Named P1 tests (test-strategy §16):** `ApplicationContextIT` (5), `MetaControllerIT` (3), `ProblemDetailsIT` (8), `RequestIdFilterTest` (5), `LogRedactionTest` (17 including parameterised cases), `HealthIndicatorsIT` (3), `ArchitectureTest` (module matrix, cycles, rules 1 to 7), `TestTaggingRulesTest` (2, including negative samples), `LogAllowlistTest` (3, including a negative sample and a drift check against architecture §15), `FakeChatPortTest` (10), and frontend `App.test.tsx` (5).
  - **Additional:**
    - Adapter contract tests: `AnthropicChatAdapterIT` (7: no sampling parameters, `output_config`, effort, `x-api-key` and no Bearer token, refusal-fallback beta on `REASONING`, refusal, invalid JSON, 2 retries, guard blocks before sending), `GeminiChatAdapterIT` (4), `OllamaAdaptersIT` (4).
    - API and tooling: `OpenApiSnapshotIT`, `PromptLintTest` (11), `FixtureScannerTest` (5), `RequirementsReportTest` (2).
    - Shared types and infrastructure: `BackoffTest` (4), `StartupGuardsTest` (3), `SharedTypesTest` (5), `FakeEmbeddingPortTest` (2), `BlockExternalHostsExtensionTest` (3).
    - Frontend: `i18n.test.ts` (4), `client.test.ts` (5).
  - **Requirement report:** FR-01 to FR-04, NFR-04, NFR-05, NFR-06, NFR-09, NFR-15, NFR-18 and NFR-19 each have at least one test.
- **Coverage of touched packages:**
  - **Backend overall: 788/893 lines (88.2 %).** The ≥ 70 % gate passes. The ≥ 80 % package gates for `verify`, `rules`, `privacy`, `retrieval` and `shared.text` apply from the phase that adds code there; those packages are empty.

  | Package | Lines | Coverage |
  | --- | --- | --- |
  | `bd.shromikai.api` | 25/25 | 100.0 % |
  | `bd.shromikai.api.internal` | 133/173 | 76.9 % |
  | `bd.shromikai.law`, `law.internal` | 16/16 | 100.0 % |
  | `bd.shromikai.shared` | 10/10 | 100.0 % |
  | `bd.shromikai.shared.ai` | 57/66 | 86.4 % |
  | `bd.shromikai.shared.ai.anthropic` | 77/82 | 93.9 % |
  | `bd.shromikai.shared.ai.fake` | 132/146 | 90.4 % |
  | `bd.shromikai.shared.ai.gemini` | 64/73 | 87.7 % |
  | `bd.shromikai.shared.ai.internal` | 20/23 | 87.0 % |
  | `bd.shromikai.shared.ai.ollama` | 80/89 | 89.9 % |
  | `bd.shromikai.shared.config` | 10/10 | 100.0 % |
  | `bd.shromikai.shared.json` | 22/28 | 78.6 % |
  | `bd.shromikai.shared.logging` | 56/58 | 96.6 % |
  | `bd.shromikai.shared.problem` | 67/74 | 90.5 % |
  | `bd.shromikai.shared.web` | 19/20 | 95.0 % |

  - **Frontend:** statements 39/40 (97.5 %), branches 30/32 (93.75 %). The ≥ 70 % gate passes.
- **Acceptance commands with output** (macOS, JDK 21.0.9, Node 24.15, Docker 29.9):
  - `cd backend && ./gradlew clean check requirementsReport`: `checkFixtures: OK`, `promptLint: OK`, `test` 107/107, `integrationTest` 35/35 (Testcontainers `pgvector/pgvector:0.8.7-pg17`), `jacocoTestCoverageVerification` and `spotlessCheck` pass, `BUILD SUCCESSFUL in 21s`.
  - `cd frontend && npm run lint && npm run typecheck && npm test && npm run build && npm run check:bundle`:
    - lint: `All matched files use Prettier code style!`
    - tests: `Tests 14 passed (14)`, `Statements: 97.5%`
    - build: `✓ built`
    - bundle: `initial JavaScript: 96.8 KB gzip (limit 200 KB)`
  - **Live run:** `DB_PORT=5433 docker compose up -d db`, because port 5432 was already taken on this machine. The backend ran through Gradle `bootRun` (profile `dev`, `DB_URL` on port 5433) and the frontend through `npm run dev`.
    - `GET /api/v1/meta` returned the configured model IDs and zero corpus counts.
    - `GET /actuator/health`: `db` `UP`. `ollama` was `DOWN` with `missingModels: [bge-m3, qwen2.5:7b-instruct]`, because a local Ollama 0.40.15 answered without the models pulled.
    - An unknown route with `Accept-Language: en` returned a `not-found` problem document.
    - <http://localhost:5173> showed **চালু (সংস্করণ 0.1.0)** with no console errors.
  - **Not yet run:** CI on GitHub (nothing pushed), and the IntelliJ run configuration (the backend ran through the same Gradle `bootRun` task from the terminal).
- **Key decisions and ADRs:**
  1. **`promptLint` exists from P1.** It checks AGENTS.md §5.3 rules 1, 2 and 4 to 7, the LOCK file and the mirror documents. It is tested on synthetic samples and passes on zero real prompts. Rule 3 (escaping) is tested on `PromptRenderer` in P5.
  2. **Thin `GeminiChatAdapter` in P1.** It uses JSON mode, temperature 0 and the key in a header, with a WireMock test. `SupportCheckContractTest` stays in P6.
  3. **springdoc moved from P7 to P1 (ADR-0001).** The frontend types are generated from a committed OpenAPI snapshot. `OpenApiSnapshotIT` and the CI type-drift step keep the snapshot, the API and the types in sync. Every property is required, and JSpecify `@Nullable` components are marked `nullable`.
  4. **`FailClosedEgressGuard` blocks every cloud call until the privacy module provides the real guard (R-03).** Each cloud adapter requires a guard and calls it before sending. Ollama calls are local and fail closed with `local-model-unavailable`.
  5. **Log lines carry only the architecture §15 keys, plus redaction.** Project loggers emit message templates, never formatted arguments. Throwables are reduced to `reasonCode` = the exception class name. Third-party messages and all values pass through `LogRedactor` (NID, mobile numbers in both digit forms, e-mail, text over 200 characters).
  6. **`Problem.instance` is the route template plus `#` plus the request ID, never the raw path.** Raw paths can carry identifiers. Braces are percent-encoded. Two codes were added to api.md §7: `method-not-allowed` (405) and `not-acceptable` (406).
  7. **`*Test` classes must carry the `unit` tag, and the `test` task includes only `unit`.** An untagged unit test therefore cannot be skipped silently. Test methods outside a `*Test` or `*IT` class fail `TestTaggingRulesTest`.
  8. **Anthropic SDK 3.7 sends `apiKey` as a Bearer token.** The adapter sets `x-api-key` explicitly, and the contract test asserts that no `Authorization` header is sent.
  9. **Version log in ADR-0001.** Spring AI 1.1.8, Boot 3.5.16, the SDK, test libraries, image tags and frontend toolchain versions are recorded there. TypeScript moved to 5.9 because MSW 3 needs it. No new ADR: every dependency is in ADR-0001's table.
- **Known gaps:**
  - **Test-first order was not strict.** In several places the production code and its tests were written in the same step, with the tests run before integration. DoD item 2 asks for tests before code.
  - **`ModelGateway`, `PromptRegistry`, `PromptRenderer` and `LlmCallListener` are not built yet.** They arrive with the first prompt (P4/P5). Until then nothing validates and repairs model output in production code, apart from the fakes' fixture validation.
  - **Cloud calls are impossible until P5 replaces `FailClosedEgressGuard`.** P4 (`query-rewrite`) runs in parallel with P5 and must wait for the guard or coordinate with it.
  - **The Claude adapter sends the prompt's schema unchanged.** Reducing it to the subset the API accepts (ADR-0005) is left to P4/P6, when real schemas exist.
  - **`OllamaEmbeddingAdapter` has no per-call timeout of its own.** It relies on the Spring AI HTTP client. Chat calls do enforce the prompt timeout.
  - **JSpecify `@NullMarked` is not applied package-wide yet.**
  - **The OpenAPI document keeps a cosmetic `"default": null` on the `Meta` and `MetaCorpus` component schemas.** It comes from springdoc.
  - **`/api/v1/meta` returns `corpus.release` and `lastRefreshAt` as `null` until P3, and `audit` as `null` until P9.**
  - **Not done in this session:** `scripts/dev-up.sh` was syntax-checked but not run end to end, because the models are several GB on a slow link. The GitHub Actions run is pending.
  - **DoD items 10 and 11 are still open:** a human runs the acceptance check, then Prompt R reviews in a fresh session. The `phase-1` tag is created locally as the prompt asks and must be moved if a review fix changes the code.
- **Open questions for the team:**
  1. Who runs the P1 acceptance check on their own machine, including the IntelliJ run configuration and `./scripts/dev-up.sh` with the model pulls?
  2. Shall we push `main` and the `phase-1` tag now so CI runs, before the Prompt R review?
  3. Port 5432 is taken on the development Mac. Should the default `DB_PORT` stay 5432?
- **Next phase prerequisites:**
  - **P2:** `JWT_SECRET` and `DEMO_PASSWORD` in `.env` and the IntelliJ run configuration; React Router is added in P2.
  - **P3:** a decision on automated fetching (P0 question 4).
  - **P4/P5:** Ollama models pulled (`./scripts/dev-up.sh`), and the real `EgressGuard` (P5) before any cloud call.

## Fix report - P1 review (PASS WITH FIXES, 11 items)
- **Root cause:** the P1 code was checked against the happy path. Nothing asserted the edge cases
  the review found: separated phone digits, framework statuses without a specific code, the
  ERROR dispatch, client-facing reason codes, `$ref` nullability. Each fix starts with a test for it.
- **Fixes (test first; every new test failed before its fix):**
  1. `LogRedactor` accepts a space or hyphen after `+880` and between digit groups. `LogRedactionTest` covers 5 formats in Latin and Bangla digits. `FixtureScannerTest` rejects `+880 1712-345678` (FR-03, R-02).
  2. Rule 3 now covers only `com.anthropic..` and Spring AI `chat`, `embedding`, `model` and `ollama`. `shouldAllowMcp_toUseSpringAiMcpAndToolTypes` passes, using a test-only `org.springframework.ai.mcp` stand-in (NFR-15; unblocks P8).
  3. `createResponseEntity` and `/error` keep the framework status. `ResponseStatusException(409)` now returns 409 with code `validation-failed`, where it used to return 400 (FR-02).
  4. `ShromikaiProperties.Provider.toString()` masks the key. `ShromikaiPropertiesTest` covers `Provider`, `Providers` and `ShromikaiProperties`.
  5. `RequestIdFilter` reuses the request attribute on ERROR dispatch. `ProblemDetailsIT.shouldKeepTheRequestId_whenAFilterThrows` checks that the header, `traceId` and the MDC ID seen by the failing filter are all equal (FR-03).
  6. `ArchitectureTest` builds every rule per root package. Samples in `testsupport.archsamples` violate each rule, and each rule must report its sample.
  7. Cloud adapters map provider 4xx (other than 408/429) to `ModelRequestRejectedException`, which is `internal-error` and never retried. They pass `Retry-After` through on 429/503 (`RetryAfter`, delay-seconds or HTTP-date). `ModelException` keeps the reason code out of `detail`; it goes to logs only (NFR-04).
  8. jsoup and PDFBox are removed from `build.gradle` until P3 and P5. ADR-0001 version log updated.
  9. OpenAPI has `Problem` and `ProblemError` as every operation's `default` response. `Meta.audit` and `latestEval` are typed, and nullable `$ref`s are emitted as `allOf` + `nullable`. `types.ts` is regenerated and the hand-written `Problem` interface is deleted.
  10. ADR-0009 covers the trigger-only approach for `audit_event` and the P9 anonymisation path, which is a `SECURITY DEFINER` function with a transaction-local flag. Architecture §11.3 and T-15 were updated, and `ApplicationContextIT` now also asserts that `TRUNCATE` fails.
  11. `OllamaHealthIndicator` is `@Profile("!fake-models")` (`FakeModelsHealthIT`). Gemini strips `$schema` and `$id`. `bn.json` has no English words, enforced by a new i18n test. The version is shown in Bangla digits (`formatVersion`).
- **Acceptance commands:** JDK 21 (Gradle 8.14 does not run on the default JDK 25).
  - `cd backend && ./gradlew clean check requirementsReport`: `test` 139/139, `integrationTest` 43/43, `checkFixtures: OK`, `promptLint: OK`, `BUILD SUCCESSFUL`.
  - `cd frontend && npm run lint && npm run typecheck && npm test && npm run build && npm run check:bundle`: 17/17 tests, 96.9 KB gzip.
- **Known gaps:**
  - Unmapped 4xx framework statuses carry the code `validation-failed`, because api.md §7 has no generic conflict code.
  - The ArchUnit `org.springframework.ai.mcp` stand-in is removed once P8 adds the MCP starter.
  - DoD items 10 and 11 must be re-run: the human acceptance check, then Prompt R on the fixed commit.

## Phase report - P2
- **Summary:** Stateless JWT login with rotating refresh tokens, roles WORKER, HR and ADMIN, guest and one-click demo access, account lockout, rate limits, an append-only audit trail, security headers and CORS, and the Bangla login, protected-route and demo-route frontend (FR-05 to FR-11).
- **Files created or changed:**
  - Backend `auth` (`JwtService`, `AuthService`, `AuthRateLimiter`, `GuestQuotaService`, repositories, `DemoAccountSeeder`), `trace` (`AuditService`, `AuditEvent`, closed JSON Schema for the detail), `shared.Hashing`.
  - Backend `api`: `AuthController`, `DemoAuthController`, DTOs, and in `api.internal` the `SecurityConfig` with its filters and problem handlers.
  - `V3__identity.sql`. `application.yml` (`shromikai.auth.*`, `shromikai.quotas.*`), `StartupGuards` (JWT secret length).
  - Frontend `api/auth.ts`, `auth/*`, `pages/*`, new routes, `bn` and `en` strings, regenerated OpenAPI types.
  - Docs: `api.md` §2.1 and the object table, `architecture.md` (`app_user.locale`, guest concurrency, T-23), ADR-0010, README.
- **Tests added and passing:**
  - Backend: unit 160/160, integration 127/127, `./gradlew check requirementsReport` BUILD SUCCESSFUL, `promptLint: OK`, `checkFixtures: OK`.
  - New classes (about 80 tests): `JwtServiceTest` (13), `GuestQuotaServiceTest` (5), `DemoProfileGuardTest` (3), `AuthControllerIT` (19), `RoleAccessIT` (23 matrix cases plus 4), `GuestTokenIT` (7), `RateLimitIT`, `RateLimitPerUserIT`, `DemoLoginIT` (10), `DemoLoginDisabledIT` (2), `AuditEventIT` (7), `SecurityHeadersIT` (7), `SecurityHeadersProdIT`.
  - Updated: `ApplicationContextIT` (V3 applied, identity tables, `citext`), `ProblemDetailsIT` (now calls as a signed-in worker, because unknown routes need a session).
  - Frontend: 39/39 (`LoginForm` 7, session and logout 11, demo login 4, existing 17 moved or kept). Lint, typecheck and build pass; initial JavaScript is 113.7 KB gzip (limit 200); `npm audit --omit=dev`: 0 vulnerabilities.
- **Coverage of touched packages (JaCoCo line):** `auth` 97 % (289/299), `auth.internal` 97 %, `trace` 92 %, `api` 93 %, `api.internal` 91 %, whole backend 93 % (1541/1665).
- **Acceptance commands with output:**
  - `cd backend && ./gradlew check requirementsReport`: `BUILD SUCCESSFUL`.
  - `cd frontend && npm run lint && npm run typecheck && npm test && npm run build && npm run check:bundle`: all pass, `initial JavaScript: 113.7 KB gzip`.
  - Browser, backend with `SPRING_PROFILES_ACTIVE=demo,dev` on port 8081 (port 8080 was held by another running instance) and Vite on 5174: `/demo-user` landed on `/app`; a reload of `/admin` kept the session through the refresh cookie and showed the Bangla 403 page for the worker; `/demo-admin` landed on `/admin` with the admin link; `/demo-hr` landed on `/app`; sign out sent a reload of `/app` to the login page and left `localStorage`, `sessionStorage` and `document.cookie` empty; the empty login form showed both Bangla validation messages and a wrong password showed `ইমেইল বা পাসওয়ার্ড ভুল।`. Response headers carried CSP, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` and `no-store`; the backend log held no JWT and no cookie name; four audit rows had only reason codes and roles.
  - Not run in a browser: a normal (non-demo) successful login, because the demo password was random and not shown. `AuthControllerIT` and the frontend tests cover it.
- **Key decisions and ADRs:** ADR-0010 (Spring Security, Nimbus, Bucket4j, React Router; no CSRF token because only the SameSite=Strict refresh cookie authenticates by cookie; URL-pattern authorisation with a default of authenticated; in-memory rate limits and guest quotas for P2). `app_user.locale` was added to V3 because `Me` and `UserInput` carry it.
- **Known gaps:**
  - Guest quota counters are in memory until `usage_counter` (V6, P7). The analyses endpoint does not exist yet; `GuestTokenIT` uses a test-only probe controller on the real path, and the real controller replaces it in P6.
  - The same holds for admin, playbook, review-item and law paths (probe controllers, `@Hidden`, test profile only).
  - Rate-limit buckets are per instance. The client IP is the connection's remote address, so behind a proxy all users would share one bucket until P11 trusts the proxy's forwarded header.
  - `PATCH /auth/me` (FR-56, P7), `DELETE /auth/me` (FR-78, P9) and API keys (P8) are not implemented. Only the password change (E-10) is.
  - The access token of a disabled user stays valid up to 15 minutes. Disabled users cannot log in or refresh.
  - `DemoAuthController` reuses `AuthController` for its cookie logic by composition; a small shared helper would be cleaner.
  - AGENTS.md §7 items 10 and 11 are open: the human acceptance check and Prompt R in a fresh session. The tag `phase-2` is therefore **not** created yet.
- **Open questions for the team:**
  1. Run the acceptance check and Prompt R, then tag `phase-2` on the commit that passes.
  2. Should the HR demo land on `/app` until the playbook page exists in P9 (current behaviour)?
  3. A process (probably the IDE run configuration) holds port 8080 with the old code; restart it so it picks up V3 and the new `JWT_SECRET` requirement.
- **Next phase prerequisites:** `JWT_SECRET` (and `DEMO_PASSWORD` for demo) in `.env` and the IntelliJ run configuration. P3 needs the decision on automated fetching (P0 question 4).

## Phase report - P3 (statute corpus, knowledge graph, quality report)

Status: code, tests, documentation and the **live ingestion run are done**; the **ILO conventions
and the acceptance check are still open** (see Known gaps). The tag `phase-3` is therefore **not**
created.

- **Summary:**
  - A versioned, hash-tracked statute corpus pipeline for the Bangladesh pack: polite scraper,
    parsers for the bdlaws index and print view, amendment footnotes turned into version labels,
    a section graph (`REFERS_TO`, `USES_DEFINITION`, `AMENDED_BY`, `ALIGNS_WITH`), idempotent
    upsert with one corpus release per run and a quality report that fails the run on a missing
    source URL.
  - `GET /api/v1/meta` now shows the latest release, counts and refresh time.
- **Files created or changed:**
  - Backend `shared.text` (`BanglaNormalizer`, `NormalisedText`, `QuoteMatcher`), `shared.Hashing`
    (byte overload); `law` (`SectionKey`, `SectionNumbers`, `IloMappingLoader`, `IngestOutcome`,
    `LawIngestService`) and `law.internal` (scraper: `PoliteFetcher`, `RobotsPolicy`, `RawCache`,
    `ActAcquirer`, `PdfActParser`; parsing: `ActParser`, `AmendmentParser`, `SubsectionSplitter`,
    `BanglaDates`; graph: `CrossReferenceExtractor`, `DefinitionExtractor`; storage:
    `LawRepository`, `VersionPlanner`, `IngestOrchestrator`, `QualityReport`; wiring:
    `PackManifest`, properties, CLI runner, ILO mapping startup check); `api.AdminLawController`.
  - `V4__corpus.sql`; `application.yml` (`shromikai.scraper.*`, `shromikai.ingest.*`);
    `data/packs/bd.yaml`, `data/mappings/bd-ilo.yaml` (no rows yet); Gradle task `ingestLaws`.
  - Fixtures: `backend/src/test/resources/fixtures/law/` (saved rows of the real pages, provenance in
    `PROVENANCE.md`). Regenerated OpenAPI snapshot and frontend types.
  - Docs: ADR-0011, ADR-0001 version log, architecture §11.2 and §18.4, api.md E-41, README.
- **Tests added and passing:**
  - Unit 308/308 (148 new), integration 134/134 (7 new), `./gradlew check requirementsReport`
    BUILD SUCCESSFUL, `promptLint: OK`, `checkFixtures: OK`; frontend lint, typecheck, tests and
    build pass.
  - New: `BanglaNormalizerPropertyTest` (7 jqwik properties), `BanglaNormalizerTest` (9),
    `QuoteMatcherTest` (8), `SectionNumbersTest`, `SectionParserGoldenTest` (13),
    `AmendmentParserTest` (11), `CrossReferenceExtractorTest` (31), `DefinitionExtractorTest` (4),
    `ScraperPolicyTest` (19, WireMock), `IngestCliRunnerTest` (5), `PackManifestTest` (3), `QualityReportTest` (10),
    `IloMappingLoaderTest` (12), `IngestionIdempotencyIT` (7).
  - Updated: `ApplicationContextIT` (V4), `MetaControllerIT` (`section_key`), `TestTaggingRulesTest`
    (reads jqwik's `@Tag` and `@Property`).
- **Coverage of touched packages (JaCoCo line):** `shared.text` 100 % (85/85, gate 80 %), `law` 90 %
  (137/153), `law.internal` 88 % (1366/1544), `api` 94 %, whole backend 91 % (3120/3438).
- **Acceptance commands with output:**
  - `cd backend && ./gradlew check requirementsReport`: `BUILD SUCCESSFUL`.
  - `cd backend && ./gradlew ingestLaws` against the live portal: first attempt failed (see the
    follow-up below), after the fix it printed `ingestion finished ... status SUCCEEDED` and created
    corpus release `2026.11.11-1` (2 acts, 563 current sections in the dev database). The run takes
    about 47 s, most of it the portal building `/act-print-952.html`.
- **Quality report summary** (from `eval/reports/corpus-quality.md`, release `2026.11.11-1`, git
  `3485159` plus the uncommitted fixes of the follow-up; status PASSED):
  - BLA-2006: 369 versions, 368 current, 1 omitted, 0 of 368 empty texts, 0 of 369 missing source
    URL, 0 duplicate numbers; 5 amending acts detected from footnotes.
  - CA-1872: 197 versions, 195 current, 2 omitted, 0 of 195 empty texts, 0 of 197 missing source URL,
    0 duplicate numbers; 4 amending acts detected.
  - BLR-2015: not published on the portal (recorded as missing, optional).
  - Edges: REFERS_TO 176, USES_DEFINITION 366, AMENDED_BY 0 (nothing was ingested earlier),
    ALIGNS_WITH 0; 2 references unresolved. "Missing translation" is 368 of 368 and 195 of 195: the
    portal publishes one language per act.
  - ILO: C001, C029, C095 and C132 skipped as unreviewed. The report lists 5 sampled sections with
    seed `1941556536324032468` for your manual comparison.
- **Key decisions and ADRs:** ADR-0011 (corpus acquisition, versioning and section keys).
  Highlights: print view plus index instead of ~370 section requests; amending acts read from
  footnote citations; versions are never reconstructed; omitted sections stored non-current;
  Bangla suffix letters mapped to Latin letters for keys; NORMLEX saved by hand because of the
  Cloudflare challenge (no bypass); one run at a time through a PostgreSQL advisory lock; the whole
  run commits or rolls back, so a failed quality check leaves the corpus unchanged.
- **Known gaps:**
  - ILO conventions are not ingested: the pack lists C001, C029, C095 and
    C132 as proposed (by the playbook), none has a `reviewed_by`, and no NORMLEX page has been
    saved. The NORMLEX parser is not written because the page structure has not been seen;
    Bangladesh's ratification status is therefore not recorded yet.
  - `data/mappings/bd-ilo.yaml` has no rows. Drafting rows needs the ILO texts in the corpus
    (quotes are copied from it, never from memory).
  - Older versions of amended sections do not exist: the portal publishes only current text. Only
    re-ingestion over time creates non-current versions. `effective_from` is set for original text
    only (act publication date); amended versions have none.
  - Sub-sections are parsed (for references) but not stored as rows. `USES_DEFINITION` matches
    terms by word start, so it is generous (almost every section links to the definitions section).
  - The Labour Rules 2015 were not in the portal's alphabetical index on 2026-11-11; the pack records
    them as optional and the run reports them as missing.
  - The PDF fallback has a test but no real PDF source configured (the pack has no `pdf_url`).
  - Tests and code were written in the same batches, not strictly red-first; the golden
    expectations were checked against the saved pages by hand.
  - AGENTS.md §7 items 10 and 11 are open (human acceptance check, Prompt R in a fresh session).
- **Open questions for the team:**
  1. Save the four NORMLEX pages (text and Bangladesh ratification) under `data/raw/ilo/` and name
     the legal reviewer who sets `reviewed_by` in `data/packs/bd.yaml`.
  2. Compare the 5 sampled sections of the generated report with the official site.
  3. Run Prompt R in a fresh session, then tag `phase-3` on the commit that passes.
- **Next phase prerequisites:** P4 needs the corpus ingested (this phase's real run) and Ollama
  with `bge-m3` pulled for the embedding backfill.

### P3 follow-up: `ingestLaws` failed with empty log placeholders

- **Symptom:** the log showed `acquiring act {}` and, 33 s later, `ingestion could not run: {}`.
- **Why the placeholders were empty:** by design, not by accident. `AllowlistJsonFormatter` prints
  only the message *template* for `bd.shromikai.*` loggers; values reach the log only as
  allowlisted keys (NFR-18, R-02). The P3 code passed values as `{}` arguments, so they were
  dropped. Fixed: the runner, orchestrator and fetcher log `errorCode`, `reasonCode` (exception
  class), `httpStatus` and `status`; failure lines are reduced to leading code words (for example
  `ACT_FAILED/BLA-2006/HTML_UNUSABLE`), so URLs and free text stay in the report only.
  `IngestCliRunnerTest` failed first with exactly the literal `{}` lines.
- **Real cause:** `/act-print-952.html` (2 MB) is built slowly by the portal: first byte after 0.26 s,
  whole page after about 20 s (measured with curl, also with gzip, which only shrinks it to 215 KB),
  and the scraper's 30 s limit is for the *whole* response, so a slower moment ended in
  `SocketTimeoutException: Read timeout`. jsoup reports a slow body as an `UncheckedIOException`,
  which the fetcher did not catch, so there was no retry and no per-act report, and the run died.
- **Fixes:** the body is read inside the request method and a slow body becomes a retryable
  `FetchException` (`UNREACHABLE`, at most 2 retries); an unexpected exception while acquiring an
  act becomes that act's failure in the report; `shromikai.scraper.timeout` is 180 s. Two WireMock
  tests reproduce the failure first (a dribbling body).
- **Checked and unchanged:** the scraper only sends `GET` (`ScraperPolicyTest` asserts no other
  method reaches the server); `robots.txt` handling is unchanged; the contact from `.env` appears
  only in the User-Agent.


## Phase report - P4 (hybrid retrieval, graph expansion, query rewriting, law API)

Status: code, tests, documentation and the live embedding and evaluation runs are done. The
**FR-23 acceptance comparison is not met** (hybrid + graph is below vector only on hit@5; explained
and with a proposed fix below), the **query-rewrite row was not run** (no `ANTHROPIC_API_KEY`), and
AGENTS.md §7 items 10 and 11 (human acceptance check, Prompt R in a fresh session) are open. The tag
`phase-4` is therefore **not** created, as for P3.

> **Update (P4 follow-up, ADR-0013):** the FR-23 comparison is now met (hybrid + graph hit@5 40/40);
> see the follow-up section below. The rewrite row is deliberately left "not run" until an
> `ANTHROPIC_API_KEY` is available, and `phase-4` is still not created (human acceptance and Prompt R open).

- **Summary:**
  - Embedding backfill (bge-m3, skips unchanged sections, V5 tracking columns), hybrid search
    (pgvector top 20 + full-text top 20, RRF k = 60, top 8), one-hop graph context (at most 4),
    topic-map additions (at most 4), Caffeine query cache, `query-rewrite` v1 behind a new
    `ModelGateway`, and the four `/api/v1/laws` endpoints. Free-text search is local only.
  - `RetrievalMetrics` is shared with the future smoke service; `./gradlew evalRetrieval` wrote
    `eval/reports/retrieval.md` at git `79baa9f` over 40 draft questions.
  - Result: vector only beat hybrid on this corpus. The text list adds noise (details below).
- **Files created or changed:**
  - `retrieval/`: `RrfFusion`, `RetrievalMetrics`, `RetrievalService`, `SearchOptions`, `Retrieval`,
    `RetrievalResult`, `RetrievalOrigin`, `QueryRewriter`, `EmbeddingBackfillService`,
    `BackfillResult`; `retrieval/internal/`: repository, `GraphExpander`, `TopicMap`,
    `RetrievalCache`, `ModelGatewayQueryRewriter`, `EmbeddingCliRunner`, properties.
  - `shared/ai/`: `Prompt`, `PromptRegistry`, `PromptRenderer`, `ModelGateway`.
  - `law/`: `LawReadService`, `Act`, `ActSummary`, `Section`, `SectionRef`, `law/internal/LawReadRepository`.
  - `api/`: `LawController`, `api/internal/SearchRateLimiter`, `ApiProperties`.
  - `V5__embedding_tracking.sql`; `prompts/query-rewrite/v1.md`, its schema and
    `docs/prompts/query-rewrite.md`; `data/mappings/bd-topics.yaml` (draft);
    `eval/retrieval/questions.jsonl`; `build.gradle` (Caffeine, `embedLaws`, `evalRetrieval`);
    `application.yml` (`shromikai.retrieval`, `shromikai.api`); OpenAPI snapshot and generated
    frontend types; ADR-0012; architecture §8 and api.md clarifications.
- **Tests added and passing:** 65 new tests, all green; `./gradlew check` (spotless, ArchUnit,
  `promptLint`, fixture scanner, coverage gates) and the frontend lint, typecheck and tests pass
  (507 backend tests in total). Unit: `RrfFusionTest` 6, `RetrievalMetricsTest` 6,
  `GraphExpanderTest` 3, `TopicMapTest` 3, `RetrievalCacheTest` 4, `QueryRewriteContractTest` 7,
  `EmbeddingCliRunnerTest` 2, `PromptRendererTest` 3, `ModelGatewayTest` 4,
  `SearchRateLimiterTest` 2. Integration (Testcontainers + `FakeEmbeddingPort`): `HybridSearchIT` 6,
  `GraphExpansionIT` 5, `EmbeddingBackfillIT` 5, `LawApiIT` 9. `RetrievalEvalRunnerIT`
  (`@Tag("eval")`) produced the table below. The `RoleAccessIT` probe for `/api/v1/laws` was
  removed because the real controller replaced it (still 200 for every signed-in role).
- **Coverage of touched packages:** `retrieval` 93 % of lines (368 of 396; gate 80 %), of which
  `retrieval.internal` 95 %; `shared.ai` 88 % (66 of 75); backend overall 91 %.
- **Acceptance commands with output:**
  - `./gradlew embedLaws`: `embedded=566 skipped=0 truncated=1` in 6 min 40 s on local Ollama (one
    truncated section: `BLA-2006:2@amend-2026`, the definitions section). A second run embeds
    nothing (`EmbeddingBackfillIT`).
  - `./gradlew evalRetrieval` (corpus release 2026.11.11-1, 566 sections embedded):

| Method | hit@5 | MRR@10 | recall@8 | mean ms/query |
| --- | ---: | ---: | ---: | ---: |
| Vector only | 1.000 (40/40) | 0.963 | 1.000 | 141 |
| Text only | 0.150 (6/40) | 0.081 | 0.225 | 21 |
| Hybrid (RRF) | 0.900 (36/40) | 0.725 | 0.950 | 58 |
| Hybrid + graph | 0.900 (36/40) | 0.725 | 0.963 | 56 |
| Hybrid + graph + rewrite | not run | not run | not run | not run |

  By question language:

| Method | Bangla hit@5 | Bangla MRR@10 | English hit@5 | English MRR@10 |
| --- | ---: | ---: | ---: | ---: |
| Vector only | 1.000 (19/19) | 0.947 | 1.000 (21/21) | 0.976 |
| Text only | 0.211 (4/19) | 0.101 | 0.095 (2/21) | 0.063 |
| Hybrid (RRF) | 0.789 (15/19) | 0.549 | 1.000 (21/21) | 0.885 |
| Hybrid + graph | 0.789 (15/19) | 0.549 | 1.000 (21/21) | 0.885 |

  Report: `eval/reports/retrieval.md` / `.json` (generated; nothing edited by hand).
- **FR-23 acceptance check: not met, with the reason.** Hybrid + graph hit@5 is 36/40, vector only
  40/40. Targets (not results) are hit@5 ≥ 0.80 and recall@8 ≥ 0.85; hybrid + graph is above both
  on these draft questions (0.900, 0.963), but the clause "at least as good as the best single
  method" fails. Why:
  - The text list is weak: text only 6/40 (Bangla 4/19, English 2/21). English questions cannot
    match lexically, because 368 of the 563 current sections are in Bangla only (BLA-2006) and the
    text index has no translation.
  - For Bangla the OR query is dominated by function words. Measured on the development database
    (`ts_stat`, diagnostic only): `কোন` is in 313 of 563 current BD sections, `বা` in 264, `এবং` in
    251, `জন্য` in 233. They outrank the one or two discriminating words.
  - Plain RRF gives the weak text list the same weight as the strong vector list, so a text rank 1
    on a wrong section pushes a correct vector rank 1 down. All four misses are Bangla questions
    (rq-004, rq-014, rq-032, rq-040; listed with their top results in the report).
  - Graph expansion raised recall@8 from 0.950 to 0.963 and did not change hit@5 or MRR (it only
    appends context after the ranked list).
- **Proposed fix (not applied):** (1) build the text query from discriminating lexemes only: drop
  lexemes whose document frequency is above a threshold, or weight by IDF; (2) for a query whose
  script differs from the act's authoritative language, let the text list contribute less or not at
  all; (3) only if (1) and (2) are not enough, weighted RRF (a change to FR-19 and an ADR). I did
  not apply any of them: the 40 questions are the test split, and tuning on them would make the
  next table a training result (evaluation.md §11). Needed first: a separate `dev` question set of
  at least 30 questions, written and checked by a team member, to tune on; then one run on the
  test set.
- **Key decisions and ADRs:** ADR-0012 (Caffeine; V5 columns; the gateway arrives now; OR text
  query; definitions-first graph order; topic-map resolution; evaluation design).
- **Known gaps:**
  - The `Hybrid + graph + rewrite` row is **not run**: `ANTHROPIC_API_KEY` is empty in `.env`. The
    code path is covered by `QueryRewriteContractTest` against a WireMock stand-in whose response
    bodies are hand-written in the Messages API shape (not recorded from a live call). In a running
    application every cloud call is blocked by the fail-closed egress guard until P5, so rewriting
    always falls back to the raw clause for now.
  - All 40 questions and the topic map are `draft`: drafted by me from the ingested text, not
    reviewed. The eval report says so and shows `checked` results separately (0 of 40).
  - `bd-topics.yaml` maps 16 of the 22 clause types; the others have no clear section in the
    ingested acts and are left out on purpose. `expected_for` is empty (the corpus has no
    `WORKER_CATEGORY` terms). The topic map is off in every ablation row.
  - The corpus has no ILO sections, so the jurisdiction filter is tested on fixtures only.
  - The truncated-section list is in `law_section.embedding_truncated`, not in the P3 quality
    report (FR-18 asks for the quality report); the count is in the `embedLaws` output.
  - `query-rewrite` v1 is `experimental` and not in `prompts/LOCK`; there is no dev-split report
    for it yet (AGENTS.md §7 item 6). No cost ledger or `LlmCallListener` yet (P6/P7).
  - `lang` is accepted and does not filter; `RetrievalSmokeService` and the admin smoke endpoint
    are P11; the 60 per minute search limit is tested at unit level, not through HTTP.
  - `RrfFusion` and `RetrievalMetrics` were written just before their tests, and the other tests in
    the same batches as the code, not strictly red-first.
  - Environment note: Gradle's default JDK here is 25 and Spotless fails on it; use
    `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (as the README says).
  - AGENTS.md §7 items 10 and 11 are open.
- **Open questions for the team:**
  1. Put `ANTHROPIC_API_KEY` in `.env` and re-run `./gradlew evalRetrieval` to fill the rewrite row
     (about 40 Haiku calls).
  2. Review and check the 40 questions (`status: checked`, `checkedBy`) and `bd-topics.yaml`
     (`reviewed_by`).
  3. Approve the proposed text-list fix and assign the 30-question `dev` set.
  4. Run Prompt R in a fresh session, then `git tag phase-4` on the commit that passes.
- **Next phase prerequisites:** P5 (intake and privacy shield) replaces the fail-closed egress
  guard; P6 needs the retrieval API (`RetrievalService.search` with `SearchOptions.forClause`) and
  a decision on the text-list fix, because the candidate set feeds the risk agent.


### P4 follow-up: text-list fix on a dev set (ADR-0013)

The "proposed fix" of the P4 report was approved ("Start dev set + fix") and applied the way it was
proposed: a separate dev set first, then one run on the test set. The earlier statement
"`phase-4` not created because FR-23 is not met" now has a different reason (below).

- **Dev set:** `eval/retrieval/dev-questions.jsonl`, 37 draft questions (16 Bangla, 21 English), written
  from 37 sections that no test question expects (key sets disjoint). `RetrievalDevTuningIT`
  evaluated eight text-query variants on it with a selection rule fixed before the run (highest
  hybrid + graph hit@5, then MRR@10, then fewer knobs). Report: `eval/reports/retrieval-dev.md`
  (git `aa1c970`):

| Text query variant | Text only hit@5 | Hybrid hit@5 | Hybrid MRR@10 | Hybrid + graph hit@5 | Hybrid + graph MRR@10 | Hybrid + graph recall@8 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| untuned (P4 first run) | 0.216 (8/37) | 0.946 (35/37) | 0.709 | 0.946 (35/37) | 0.709 | 1.000 |
| df ≤ 0.2 | 0.351 (13/37) | 0.946 (35/37) | 0.771 | 0.946 (35/37) | 0.771 | 1.000 |
| df ≤ 0.1 | 0.514 (19/37) | 1.000 (37/37) | 0.830 | 1.000 (37/37) | 0.830 | 1.000 |
| df ≤ 0.05 | 0.514 (19/37) | 1.000 (37/37) | 0.812 | 1.000 (37/37) | 0.812 | 1.000 |
| language filter | 0.216 (8/37) | 0.946 (35/37) | 0.709 | 0.946 (35/37) | 0.709 | 1.000 |
| df ≤ 0.2 + language filter | 0.351 (13/37) | 0.946 (35/37) | 0.771 | 0.946 (35/37) | 0.771 | 1.000 |
| df ≤ 0.1 + language filter | 0.514 (19/37) | 1.000 (37/37) | 0.830 | 1.000 (37/37) | 0.830 | 1.000 |
| df ≤ 0.05 + language filter | 0.514 (19/37) | 1.000 (37/37) | 0.825 | 1.000 (37/37) | 0.825 | 1.000 |

  **Selected by the rule: df ≤ 0.1** (max doc freq 0.1, language filter false). The language filter changed no dev metric, so it ships off (the option stays, tested).
- **Change:** `TextQueryBuilder` drops query words that occur in more than 10 % of the current
  sections (floor of 20 sections; at least the 3 rarest words are kept); document frequencies
  come from `ts_stat` and are cached 10 minutes. RRF and its constants are unchanged.
- **Test set, run once after selection** (`eval/reports/retrieval.md`, git `aa1c970`, the same 40
  draft questions as before; the last two rows re-measure the untuned query in the same run):

| Method | hit@5 | MRR@10 | recall@8 | mean ms/query |
| --- | ---: | ---: | ---: | ---: |
| Vector only | 1.000 (40/40) | 0.963 | 1.000 | 151 |
| Text only | 0.425 (17/40) | 0.349 | 0.488 | 12 |
| Hybrid (RRF) | 1.000 (40/40) | 0.810 | 0.988 | 43 |
| Hybrid + graph | 1.000 (40/40) | 0.810 | 1.000 | 42 |
| Hybrid + graph + rewrite | not run | not run | not run | not run |
| Text only, untuned text query (reference) | 0.150 (6/40) | 0.081 | 0.225 | 20 |
| Hybrid + graph, untuned text query (reference) | 0.900 (36/40) | 0.725 | 0.963 | 53 |

  By question language:

| Method | Bangla hit@5 | Bangla MRR@10 | English hit@5 | English MRR@10 |
| --- | ---: | ---: | ---: | ---: |
| Vector only | 1.000 (19/19) | 0.947 | 1.000 (21/21) | 0.976 |
| Text only | 0.737 (14/19) | 0.603 | 0.143 (3/21) | 0.119 |
| Hybrid (RRF) | 1.000 (19/19) | 0.781 | 1.000 (21/21) | 0.837 |
| Hybrid + graph | 1.000 (19/19) | 0.781 | 1.000 (21/21) | 0.837 |
| Text only, untuned text query (reference) | 0.211 (4/19) | 0.101 | 0.095 (2/21) | 0.063 |
| Hybrid + graph, untuned text query (reference) | 0.789 (15/19) | 0.549 | 1.000 (21/21) | 0.885 |

- **Reading the result honestly:**
  - The FR-23 clause is met: hybrid + graph hit@5 is 40/40, equal to vector only (40/40). Before the
    change it was 36/40.
  - It is a tie on hit@5, not a win. Vector only still has the better MRR@10 (0.963 against 0.810)
    and the better English MRR: on this corpus, with these questions, the text list improves
    recall of the candidate set (recall@8 1.000) but does not improve the ranking over vector
    alone. Text only is still weak for English questions (3/21), as expected for a
    mostly-Bangla corpus.
  - The dev and test sets are small (37 and 40 questions) and both are drafts written by the
    same author, so differences of one or two questions are not evidence of anything.
  - Selection used only dev data. The test set was run once with the selected setting, plus the
    untuned reference rows; no other variant was run on it.
- **Still open:** the `Hybrid + graph + rewrite` row (no `ANTHROPIC_API_KEY`); review of both
  question sets and the topic map; AGENTS.md §7 items 10 and 11 (human acceptance, Prompt R), so
  `phase-4` is still **not** created. Whether to prefer vector-heavy fusion (higher MRR) is a
  decision for P6, which will see real clauses rather than questions; it needs its own ADR.

## Phase report - P5 (contract intake and local privacy shield)

Status: code, tests, documentation and ADR-0014 are done and `./gradlew check slowTest` is green on
this machine. **Not done:** the live acceptance run against real Claude and real `qwen2.5:7b-instruct`
(no `ANTHROPIC_API_KEY` here), the PII recall measurement (M-25), AGENTS.md §7 items 10 and 11
(human acceptance check, Prompt R in a fresh session). The tag `phase-5` is therefore **not**
created, as for P3 and P4 (`phase-4` is also still untagged).

- **Summary:**
  - Intake (`ingest`): magic-byte validation, PDFBox text with a memory-only stream cache, scanned
    pages and images through local Tesseract (`ben+eng`, stdin/stdout, no files), legacy-font Bangla
    conversion with OCR fallback, repeated header/footer removal, hyphenation repair, numbered
    paragraphs, language detection, per-page OCR confidence.
  - Privacy shield (`privacy`): deterministic detectors for 11 PII types, local `pii-extract`
    (two attempts, then fail closed), typed placeholders held only in the job's memory, the real
    egress guard in every cloud adapter (detectors plus Aho–Corasick over the job's values, audit
    event, metric), instruction-like-text detector.
  - Segmentation and classification (`analysis`): rule-based splitter with a `clause-segment`
    fallback, `clause-classify` with the document-type gate, in-memory `JobStore` (30 minutes),
    pipeline `INTAKE`→`OCR`→`REDACT`→`CLASSIFY` with content-free trace events, `dev`-profile
    debug endpoints, five synthetic contracts.
- **Files created or changed:**
  - `ingest/`: `IntakeService`, `IntakeProperties`, `IntakeConfiguration`, `IntakeResult`,
    `IntakeParagraph`, `PageQuality`, `InputKind`, `UnreadableDocumentException`; `ingest/internal/`:
    `MagicBytes`, `FileType`, `PdfDocument`, `ImageDecoder`, `BijoyConverter`,
    `HeaderFooterRemover`, `HyphenationRepair`, `Paragrapher`, `OcrEngine`, `TesseractCliEngine`.
  - `privacy/`: `PiiType`, `PiiMatch`, `PiiDetector`, `PlaceholderMap`, `Redactor`, `Reidentifier`,
    `LocalEntities`, `LocalEntityExtractor`, `PrivacyShield`, `RedactedParagraph`,
    `PrivacyEgressGuard`, `InjectionDetector`; `privacy/internal/`: `Keywords` (+ `keywords.yml`),
    `AhoCorasick`.
  - `analysis/`: `AnalysisService`, `AnalysisJob`, `JobStore`, `Clause`, `ClauseType`,
    `ClauseFlag`, `ClauseSegmenter`, `ClauseClassifier`, `Para`, `Stage`, `TraceEvent`.
  - `api/`: `DevAnalysisController` (profile `dev`); `GlobalProblemHandler` adds `pages` to problem
    documents. `shared/`: `EgressContext`, `ChatRequest` (optional context), `ModelGateway`
    (context overloads), `AiConfiguration` (fail-closed stand-in removed), `ProblemException`
    (`pages`). `trace/AuditService` constructor made public.
  - Prompts: `pii-extract`, `clause-segment`, `clause-classify` (v1, experimental) with schemas and
    `docs/prompts/*.md`. Config: `shromikai.intake.*`, `server.tomcat.max-swallow-size`.
  - `eval/contracts/synthetic/` (5 contracts with meta and entities files), `docker/tesseract/`,
    OCR fixtures `fixtures/ocr/`, rental-agreement fixture, `canaries.yml` filled.
  - Docs: ADR-0014, architecture §10.1 and §18.5 (C24 to C27, migration renumbering), api.md E-45
    and E-45a, prompts README, README.
- **Tests added and passing:** `./gradlew test` 476 unit tests, `integrationTest` 199, `slowTest` 3 (`OcrIT`), 0 failures, 0 skipped, in the last run. New in P5: detector table (24 positive and 9 negative rows) and 3 jqwik properties, round-trip, guard, extractor, injection, Aho–Corasick, magic bytes, Bijoy, header/footer, segmenter (5 contracts), PDF/OCR intake, pipeline ITs and the persistence scan.
  - Named tests of test-strategy §16 for P5: `MagicBytesTest`, `PdfTextExtractorIT`,
    `BijoyConverterTest`, `OcrIT` (slow), `PiiDetectorTest` (+ `PiiDetectorPropertyTest`, jqwik),
    `RedactionRoundTripTest`, `EgressGuardTest`, `NoPiiLeavesTheBoxIT`, `FailClosedIT`,
    `NoContractPersistenceIT`, `ClauseSegmenterTest`, `DocumentGateIT`, `DevEndpointIT`,
    `PromptInjectionTest`, `PromptRendererTest` (existing; covers the escaped closing tag).
  - Also: `AhoCorasickTest`, `InjectionDetectorTest`, `LocalEntityExtractorTest`,
    `HeaderFooterAndHyphenationTest`, `SyntheticContractsTest`, `DevEndpointEnabledIT`.
- **Coverage of touched packages (JaCoCo line, unit + integration):** `privacy` 97.2 % (342/352), `privacy.internal` 94.7 % (72/76), `analysis` 91.2 % (353/387), `ingest` 84.8 % (168/198), `ingest.internal` 74.4 % (293/394).
  The 80 % gate for `privacy` passes.
- **Acceptance commands with output:**
  - `./gradlew check` → `BUILD SUCCESSFUL` (tests, integration tests, JaCoCo gates, `promptLint: OK`, `checkFixtures: OK`, Spotless).
  - `./gradlew slowTest` → `OcrIT` 3 tests green on `shromikai-tesseract`: mean confidence 92.0,
    character accuracy 0.963 on the synthetic Bangla and English image (one image, so read it as a
    smoke result, not a rate); the blurred copy yields no words and fails with
    `unreadable-document`, `pages: [2]`.
  - Debug endpoint, per synthetic contract (`DevEndpointEnabledIT`, `dev` + `test` profiles): redacted
    clauses with types and confidence, expected clause counts 11, 9, 10, 11, 10, no original value in
    the response, `PRIVACY_BLOCK` count unchanged. `NoPiiLeavesTheBoxIT` (real Claude adapter and real
    guard against WireMock): zero blocked calls and no identifier in any captured request for all five
    contracts, also with the local model returning nothing for NID, phone, e-mail, passport, DOB.
  - **Not run:** the same flow against real Claude and real Ollama.
- **Key decisions and ADRs:** ADR-0014 (money, durations and employment dates stay, restating
  ADR-0003; Tesseract as a child process instead of `tess4j`; per-job `EgressContext` on
  `ChatRequest`; `CLASSIFIED` as the end state of P5; E-45a). Spec findings C24 to C27.
- **Known gaps:**
  - No live run with a real model: the `pii-extract`, `clause-segment` and `clause-classify` prompts
    were exercised against stubs and WireMock only. Their dev-split reports and M-25 (recall per PII
    type) need the evaluation runner (P10) and a key; the prompts stay `experimental` and are not
    in `LOCK`.
  - A person's name that the local model does not return is not caught by the value search (RK-02);
    detectors only catch identifiers with a pattern. The guard cannot close that gap, M-25 will measure it.
  - `BijoyConverter` maps letters, vowel signs, reph, ya-phala, ra-phala and `ক্ষ`; rarer conjunct
    glyphs are not mapped. It was tested on hand-written word pairs, not on a real Bijoy PDF; pages
    below 0.90 Bengali letters after conversion go to OCR.
  - PDF text-layer tests use English text (the test PDF builder has no Bangla font); a Bangla
    text-layer PDF was not tested. OCR was measured on one synthetic image, not on phone photos.
  - The PDF time limit is one budget (10 s plus four OCR timeouts) around the whole read, not a
    separate 10 s parse limit. There is no `AbuseLimitsIT`; the decompression-bomb PNG and the
    11 MB and 16-page cases are in `PdfTextExtractorIT` and `DevEndpointEnabledIT`.
  - `NoContractPersistenceIT` runs the pipeline on the fake model port inside Spring; the real
    cloud adapter path is covered by `NoPiiLeavesTheBoxIT`. `review_item.*` and `analysis_trace.detail`
    do not exist yet; the scan reads every text and jsonb column, so it covers them once P6 creates
    them, and it names `audit_event.detail` explicitly.
  - The temp-directory scan looks 4 levels deep at files up to 8 MB.
  - Spotless needs a JDK 21 here (Gradle on JDK 25 fails with google-java-format 1.28.0); I ran
    `JAVA_HOME=…/jdk-21.jdk ./gradlew spotlessApply check`.
  - `slowTest` needs Docker and builds the `shromikai-tesseract` image on first use (apt over the network).
- **Open questions for the team:**
  - Provide an `ANTHROPIC_API_KEY` for a live run of the dev endpoint, or accept the WireMock proof for P5?
  - Run the human acceptance check and Prompt R, then tag `phase-5` (and `phase-4`).
  - Production image: install `tesseract-ocr`, `tesseract-ocr-ben`, `tesseract-ocr-eng` (P11).
- **Next phase prerequisites:** P6 creates `V6__analysis.sql` (not V5, C26), consumes `Clause`,
  `AnalysisJob` and `JobStore`, and calls `ModelGateway` with the job's `EgressContext` for every
  cloud prompt (`clause-facts`, `risk-analysis`, …).

## Fix report - review of phases 3 to 5 (01359f9..HEAD, PASS WITH FIXES)
- **Fixed** (review item number in brackets):
  - #1 `TesseractCliEngine` read stdout to the end before `waitFor`, so a hung child blocked the
    thread forever. Stdin and stdout now run on their own threads, one deadline covers the child,
    output is capped at 8 MiB. `TesseractCliEngineTest`: sleeping child, endless output, failing child.
  - #2 `IntakeService` decoded every image up front. Images and PDF pages are now decoded inside
    their OCR slot (at most `ocr-parallel` live), OCR has a deadline on the image path too, and a
    service-wide `max-concurrent` semaphore answers `capacity-exhausted`. `AbuseLimitsIT`: 10 MB+1
    upload, 16-page PDF, 20,000 x 20,000 PNG bomb, decoded-image peak, hung OCR, global limit.
  - #4 `Redactor` matched exact UTF-16 text. It now matches case-insensitively on NFC with an offset
    map back to the original, at word boundaries, also on each name part (surname alone), minimum 3
    characters. `RedactorMatchingTest`: case, NFD/NFC, split Bangla vowel sign, surname only,
    "Ali" inside "Alibaba", short parts.
  - #5 `QueryRewriter.rewrite(clause, EgressContext)` passes the job context to `ModelGateway`;
    `RetrievalService.search` has an overload with the context (the old signature uses
    `EgressContext.NONE`, for public law questions and the eval). Test shows the guard's known-value
    stage blocks a clause the detectors cannot see.
  - #3 `NoPiiLeavesTheBoxIT` renamed `EgressPlumbingIT` (name, comment and every doc reference
    except history in this log): it proves wiring with an extractor that is handed the labelled
    entities. New negative test: an extractor that returns no person lets the name reach the cloud payload.
  - #6 `JobStore` now has a scheduled sweep (60 s) and `max-jobs` (200, `capacity-exhausted`);
    `JobStoreTest` with a fake clock.
  - #9 `PoliteFetcher`: https when the host answers on it (one probe per host, http fallback),
    redirects followed only within the request's host (`REDIRECT_OFF_HOST`), body capped at 32 MiB
    (`BODY_TOO_LARGE`, never truncated). Tests for off-host redirect, same-host redirect, cap, http fallback.
  - #10, #11 `TextRequest` validated (`@NotNull @Size(200..60000)`, `@Valid`); `StartupGuards`
    refuses `prod` together with `dev`. Tests: `DevEndpointEnabledIT`, `StartupGuardsTest`.
  - #12 `RetrievalCache` key is the SHA-256 of the normalised query.
  - #7 `RetrievalEvalRunner` now writes the MRR@10 regression, saturation and draft
    notices into the acceptance section; `eval/reports/retrieval.md` carries the same paragraphs
    (hand-applied, measured numbers untouched, to be regenerated on the next `evalRetrieval`).
  - #19 Gradle 8.14 cannot run on JDK 25 (class file 69), so `gradle/gradle-daemon-jvm.properties`
    pins the daemon to JDK 21 (the compile toolchain was already 21); `./gradlew check` and
    `spotlessApply` work from a JDK 25 shell. This replaces the earlier "use a JDK 21" workaround.
- **Known gaps (not fixed here, by decision):** ILO mapping review; weighted RRF (hybrid MRR@10 is
  below vector only); harder retrieval questions (set is saturated) and review of the 40 draft
  questions; real-Ollama `slow` test for PII recall (M-16/M-25), the plumbing test is not that;
  README details; ADR deciders; the https path of the scraper has no positive test (WireMock serves
  plain http only), only the fallback.

## Phase report - P6 (intelligence core)

Status: code, tests, documentation and ADR-0015 to ADR-0019 are done; `./gradlew check` and
`integrationTest` are green on this machine (2026-11-11). **Not done:** the human acceptance check,
Prompt R in a fresh session (AGENTS.md §7 items 10 and 11), any evaluation report (P10), the live
side-swap run and the full M-19 measurement (both skipped by decision, to stay inside a US$3 cap).
The tag `phase-6` is therefore **not** created. What the live evidence does and does not show is
in "Honest limits" below and in [`docs/known-gaps.md`](known-gaps.md).

- **Summary:**
  - `rules`: fail-fast rule loader, deterministic engine over typed `ClauseFact`s, eight Bangladesh
    rules, all `draft` (none is evaluated until a person other than the drafter reviews it).
  - `verify`: three-check citation verifier (existence and currency, quote match, independent
    support) with the decision table, review items and `VerifiedCitation` (package-private
    constructor). Only verified citations reach the `dev` endpoint (R-01).
  - `analysis`: orchestrator `RETRIEVE ∥ RULES → RISK → VERIFY → CORRECT → COMPOSE → COMPLETE`
    (`FAILED` from any state), fact extraction, risk agent, corrective loop, counter-offer agent
    with a rule re-check, finding merge, per-job budget, migration `V6__analysis.sql`.
  - Live evidence: one contract, `syn-en-02` (worker side), recorded as hash-addressed fixtures and
    replayable offline.
- **Files created or changed** (307 files since `origin/main`, main sources listed by module):
  - `rules/`: `RuleLoader`, `RuleSet`, `Rule`, `RuleEngine`, `RuleComparator`, `RuleViolation`,
    `ClauseFact`, `FactParameter`, `Unit`, `Severity`, `RulesConfiguration`, `RulesProperties`;
    `data/rules/bd-labour.yaml`, `schemas/rules.schema.json`.
  - `verify/`: `CitationVerifier`, `DecisionTable`, `VerifiedCitation`, `ClaimedCitation`,
    `Verdict`, `VerificationResult`, `ReviewItem`, `ReviewItemFactory`, `ReviewItemStore`;
    `internal/`: `ExistenceCheck`, `QuoteCheck`, `SupportVerifier`, `JdbcReviewItemStore`,
    `LawSectionLookup`.
  - `analysis/`: `AnalysisPipeline`, `ModelSession`, `RiskAgent`, `CorrectiveLoop`,
    `CounterOfferAgent`, `FactExtractor`, `RegexFacts`, `FindingMerge`, `FindingVerifier`,
    `ReviewQueue`, `CandidateRetriever`, `CandidateSet`, `Finding`, `Redline`,
    `AnalysisRunStore`, `AnalysisProperties`; `ClauseClassifier` (batch bound).
  - `shared/ai/`: dynamic enums (`SchemaEnums`, `DynamicEnum`), `LlmCallListener`, `CostCalculator`,
    `PricingProperties`, stop reason and usage in `ChatResponse`, `AnthropicSchemaSubset`, thinking
    off per model, hash-addressed `FakeChatPort`; `shared/text/OverlapChecker`.
  - `law/`: `LawReadService.lookup`; `privacy/PiiDetector` NID-over-phone tie-break; `api/`
    `DevAnalysisController` (`POST /api/v1/dev/analyses?full=true&side=`, `GET …/{id}/findings`).
  - Prompts (all `experimental`): `clause-facts` v1, `risk-analysis` v1 and v2, `risk-citation-repair`
    v1 and v2, `counter-offer` v1 and v2, `verify-support` v1 and v2, `verify-support-claude` v1 and
    v2, each with schema and `docs/prompts/*.md`; `prompts/LOCK` pins 11 released prompt files.
  - Tests and tooling: see below; `scripts/demo-fake-citation.sh`; live tooling under
    `src/test/java/bd/shromikai/testsupport/live` and `analysis/live` (opt-in with `-PliveOk`).
  - Docs: ADR-0015 to ADR-0019, architecture §5.3 budgets and §18.6 findings C28 to C44, PRD NFR-01 and
    KPI-08, `evaluation.md` M-19, `docs/known-gaps.md`, `docs/rules-review-checklist.md`.
- **Tests added and passing:** `./gradlew check`: 781 unit tests, 0 failures; `integrationTest`: 235
  tests, 0 failures; `promptLint` and `checkFixtures` OK. Named tests of the phase prompt:
  `VerificationDecisionTableTest`, `CitationOutsideRetrievedSetTest`, `FakeCitationInjectionTest`,
  `RuleLoaderFailFastTest`, `RuleEngineTest` and its jqwik property test, `RiskAgentSchemaTest`,
  `FindingMergeTest`, `CounterOfferRuleCheckIT`, `OrchestratorStateMachineTest`, `GoldenAnalysisIT`,
  `SideSymmetryIT`, `AllResponsesCarryOnlyVerifiedCitationsIT`. Added for the live runs and
  budgets: `PromptFreezeTest`, `PromptGoldenTest`, `RiskAnswerSizeTest`,
  `CounterOfferAnswerSizeTest`, `RepairAnswerSizeTest`, `PromptAnswerBoundAuditTest`,
  `CorrectiveLoopCandidatesTest`, `LiveSpendLedgerTest` (cap, per-prompt limits, tripwire),
  `AnthropicSchemaSubsetTest`, `ClauseClassifierBatchTest`. One live test (`LiveContractIT`,
  opt-in) recorded the contract; it is skipped without `-PliveOk=record`.
- **Coverage of touched packages** (JaCoCo line coverage, unit tests): `verify` 94.0%
  (406/432), `rules` 94.7% (233/246), `analysis` 94.5% (1,648/1,743), `privacy` 96.9% (467/482),
  `retrieval` 95.3% (444/466), `shared.text` 99.2% (125/126), `shared.ai` 90.9% (763/839); overall
  92.7% (7,555/8,148). Gates (80% for `verify`, `rules`, `privacy`, `retrieval`, `shared.text`)
  hold.
- **Acceptance commands with output:**
  - `./gradlew check` → `BUILD SUCCESSFUL` (`promptLint: OK`, `checkFixtures: OK`).
  - `./gradlew integrationTest` → 235 tests, 0 failures.
  - `scripts/demo-fake-citation.sh` runs offline: a model that invents a section is stopped by the
    verifier and the finding is `UNCERTAIN` without a citation.
  - Live (opt-in, real providers, cap US$3.00; ledger US$0.984 after all runs):
    `./gradlew liveTest -PliveOk=record -Pcontract=syn-en-02 --tests '*LiveContractIT'` replays the
    recorded steps and went live only for the verifier, repair and counter-offers in the last run:
    status `ANALYSED`, 11 findings, 10 VERIFIED and 1 UNCERTAIN, 0 REJECTED, 10 redlines, none cut
    or dropped; that run's live calls cost US$0.139 (verifier US$0.004, repair US$0.066,
    counter-offers US$0.069).
  - Live smoke: one Claude Haiku call succeeded; the Gemini call never produced an answer (see below).
- **Measured live cost** (ledger, every call of every run of this phase, 122 calls, US$0.984 of the
  US$3.00 cap): `risk-analysis` US$0.495 (10 calls, including the first v1 attempt that was cut at
  its cap and the batch tests), `risk-citation-repair` US$0.332 (6), `counter-offer` US$0.141
  (19), `verify-support-claude` US$0.009 (38), Haiku steps (`query-rewrite`, `clause-classify`,
  `clause-facts`) US$0.008. Much of that is money spent finding the output bounds (C38 to C42, C44),
  not the price of one analysis. The first complete run took 36 s end to end and cost about
  US$0.35 with v1 repair and counter-offer prompts; no run since has measured all stages live
  at once (the last run replayed RISK).
- **Key decisions and ADRs:**
  - ADR-0015: P6 choices (types in `rules`, dynamic enums in the gateway, per-job budget, US$3 live
    cap, cheapest model per role, rules stay `draft`, review items, `dev` endpoint, hash-addressed
    recordings, Claude thinking off).
  - ADR-0016: risk answer bound (v2, 3,000 tokens) and revised RISK budget. ADR-0017: counter-offer
    v2 (900 tokens), at most 12 candidate sections per repair call, COMPOSE 17 s, NFR-01 p95 targets
    85 s and 95 s. ADR-0018: Gemini verifier dropped for now. ADR-0019: answer-size audit of every
    prompt and the v2 prompts it produced.
- **Honest limits (what the evidence does not show):**
  - **All verification is `SAME_FAMILY`.** The independent verifier of §7.6 (Gemini Flash) was
    never run live: the key had no access to `generateContent` on the 3.x models, 2.5 is closed to
    new users, and the Interactions API would need a new adapter. Every verified citation was
    judged by Claude Haiku, the same family as the agents, and carries the warning
    `verifier-same-family`. No result may be called independently or cross-family verified. The
    Gemini adapter and the `verify-support` prompt exist but are tested only against stand-ins.
  - **One contract, one side, one recording.** `syn-en-02`, worker side. No side-swap run, no
    Bangla contract, no `test` split, no metric of M-01 to M-14 computed from live data. The
    pipeline integration tests use scripted stand-ins on a fictional act and prove behaviour, not
    model quality.
  - **One run is not a median.** The 36 s figure is a single sample; the stage budgets (84 s and
    92 s) are planning numbers until M-19 is measured (P10). The cost per analysis is likewise one
    sample, and the repair and counter-offer prompts changed after it.
  - **Recall is capped by design and not measured.** `risk-analysis` v2 reports at most three
    findings per batch (four batches). In both live runs the non-compete clause (c9) was not
    reported. Recall loss is unmeasured.
  - **`clause-facts` is not bounded.** Its schema has no limit on facts per clause, so its answer
    can pass the 600-token cap. Mitigation: at most four clauses per call, a cut call is split, a
    clause still cut gets no facts. A v2 with a bound would change the risk-step hashes and cost
    about US$0.18 to re-record. `clause-segment` and `pii-extract` are also unbounded, with a safe
    failure each (ADR-0019).
  - **Weak relevance can pass.** The support check judges the claim against the section, not
    whether the section bears on the clause: the transfer finding f9 (confidence 0.55) was verified
    against `BLA-2006:3`. One finding (f7, resignation needs permission) stays `UNCERTAIN` after
    one repair, which is the intended outcome.
  - **The local PII model was a stand-in for the live runs.** `qwen2.5:7b-instruct` in Docker
    without a GPU timed out, so the recordings use the contract's ground-truth entity list for that
    step only. PII recall on the real model is unmeasured (M-16, M-25).
  - **Rules need human review.** All eight rules are `draft`; none was evaluated in any run
    (`RULES activeRules=0, draftRules=8`). `reviewed_by` is empty and was never set by the drafter.
    The checklist is `docs/rules-review-checklist.md`.
  - **ILO mapping is empty.** `iloReferences` is `[]`; the mapping rows are unreviewed (FR-16, R-10).
  - **FR-58 window to revisit in P7.** `POST /api/v1/analyses` answers text synchronously when it
    finishes within 60 s, but the stage budgets add up to 84 s (p95 target 85 s). P7 decides
    between a shorter text limit, a longer window or always `202`.
  - Statute sections are not prompt-cached (only the system prompt is); prompts are `experimental`
    with no dev-split report; the memo, public API, re-identification and signing are P7.
- **Open questions for the team:**
  - Review the eight rules (`docs/rules-review-checklist.md`) and say "rule X reviewed" for each one
    that holds; then set `reviewed_by` with the note "team member, not a lawyer".
  - Get a Gemini key and model that work, or accept `SAME_FAMILY` for the submission and say so.
  - Is a `clause-facts` v2 worth a re-recording before more contracts are recorded?
  - Which contracts to record next (cap: 3 in total, US$1.20 each) and when to run the Bangla one.
  - Run the human acceptance check and Prompt R, then tag `phase-6`.
- **Next phase prerequisites:** P7 consumes `AnalysisPipeline` and `AnalysisJob.Outcome`
  (findings, verdicts, redlines, review items), `AnalysisService.resultFor` for re-identification
  and signing, and the `CostCalculator`/`LlmCallListener` for the cost ledger (FR-62). It must decide
  the FR-58 window, add the persistent cost ledger and daily cap, and keep the prompts' output
  bounds (`PromptAnswerBoundAuditTest` fails for any new prompt that is not bounded or listed).
