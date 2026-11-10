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
