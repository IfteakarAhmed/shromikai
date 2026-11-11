# ADR-0001: Technology stack

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | ADR-0003 (privacy), ADR-0004 (database), ADR-0005 (model routing), ADR-0006 (SSE client, envelope); playbook P1 to P11; architecture §18.1 C17 |

## Context

The event brief fixes the core stack: Java 21, Spring Boot 3.5, Spring AI 1.x, Gradle (Groovy DSL),
YAML configuration, PostgreSQL 17 + pgvector, Flyway, Ollama with bge-m3 and qwen2.5:7b-instruct,
Claude Sonnet and Claude Haiku, Gemini Flash as an optional independent verifier, React 19 +
TypeScript + Vite + Tailwind, Docker Compose and n8n. The Phase 1 foundation (commit `7209544`)
pins Spring Boot 3.5.6, Spring AI 1.0.1, Gradle 8.14.3, jsoup 1.21.2 and PDFBox 3.0.5. The master
prompt playbook names further tools per phase (Spring Security with JWT, Bucket4j, Caffeine,
java-diff-utils, springdoc, Recharts, Caddy, Cloudflare Tunnel).

## Decision

Keep the fixed stack and add the components below. Every version is pinned exactly in the phase
that introduces it (`build.gradle`, `package-lock.json`, compose files); floating tags such as
`latest` are not allowed.

**Spring AI version (amended in spec v1.1, conflict C17).** P1 replaces the Phase 1 pin 1.0.1 with
the latest **Spring AI 1.1.x** GA patch release whose BOM supports Spring Boot 3.5, and records the
exact patch version in this ADR's version log below and in `build.gradle`. The 1.1 line is
required because it adds the Streamable HTTP MCP server transport; the 1.0 line has only SSE.
Spring AI 2.x is not used because it targets Spring Boot 4, outside the fixed stack.

**MCP transport.** Streamable HTTP only: `spring-ai-starter-mcp-server-webmvc` with
`spring.ai.mcp.server.protocol=STREAMABLE` at `/mcp`, profile `mcp`. The deprecated SSE transport
is not offered, so there is exactly one transport to test (`McpServerIT`) and document
(`docs/mcp.md`).

| Concern | Choice | Phase | Reason |
| --- | --- | --- | --- |
| Model ports | `ChatPort` and `EmbeddingPort` in `shared.ai` with deterministic fakes | P1 | Testable model calls (playbook P1) |
| Claude calls | Official Anthropic Java SDK (`com.anthropic:anthropic-java`) in `AnthropicChatAdapter`; remove `spring-ai-starter-model-anthropic` | P1 | Structured outputs, `effort`, refusal handling, no sampling parameters (ADR-0005) |
| Local chat and embeddings | Spring AI Ollama starter (`OllamaChatAdapter`, `OllamaEmbeddingAdapter`) | P1 | Fixed stack; already in the Phase 1 build |
| Gemini verifier and judge | Spring `RestClient` adapter (`GeminiChatAdapter`) | P6 | One endpoint; no dependency on a Spring AI module version for an optional provider |
| Output validation | networknt `json-schema-validator` (2020-12) | P1 | R-04 |
| Data access | Spring `JdbcClient` + Flyway; no JPA | P1 | Explicit SQL for pgvector, `tsvector` and graph queries |
| Security | Spring Security resource server with HS256 JWT (Nimbus), BCrypt (strength 12), rotating refresh tokens | P2 | Playbook P2; stateless horizontal scaling |
| Rate limiting | Bucket4j (in-memory) plus `usage_counter` | P2 | Playbook P2; quotas (FR-09) |
| Scraping | jsoup with a polite client; PDFBox fallback | P3 | Already pinned; playbook P3 |
| Retrieval cache | Caffeine | P4 | Playbook P4 |
| PDF text | PDFBox 3 with a memory-only stream cache | P5 | R-02 |
| OCR | Tesseract 5 (`ben`, `eng`) through tess4j, installed in the backend image | P5 | Local (R-03); Bangla support |
| Redline diff | java-diff-utils (word level) | P7 | Playbook P7 |
| Memo PDF | Gotenberg (headless Chromium) container; print stylesheet fallback in the web app | P7, P9 | Chromium shapes Bangla conjuncts like the browser; visual test required by the playbook |
| Canonical JSON for signing | RFC 8785 JCS implementation checked against the RFC test vectors | P7 | Signed result envelope |
| OpenAPI | springdoc-openapi (`/swagger-ui`, `/v3/api-docs`) | P1 (moved from P7) | Generated frontend types from P1 (AGENTS.md §6 forbids hand-written API types); committed snapshot `frontend/src/api/openapi.json` checked by `OpenApiSnapshotIT` and the CI type-drift step |
| MCP server | Spring AI 1.1.x MCP server starter for WebMVC, Streamable HTTP transport; official MCP Java SDK client (Streamable HTTP) in tests | P8 | Playbook P8; one testable transport |
| SSE client (frontend) | Written in the project on `fetch` + `ReadableStream` (`src/api/sse.ts`); no library | P9 | `EventSource` cannot send an `Authorization` header; tokens never go into URLs (ADR-0006) |
| Canonical JSON | `shared.CanonicalJson` (RFC 8785) | P7 | Signing independent of the `analysis` types (ADR-0006) |
| Metrics | Micrometer with the Prometheus registry | P1, P10 | NFR-18 |
| Formatting and lint | Spotless with google-java-format; ESLint and Prettier | P1 | Playbook P1 |
| Frontend runtime | React Router, TanStack Query, i18next, Recharts | P1 (TanStack Query, i18next), P2 (React Router), P9 (Recharts) | Routing, server state without persistence, Bangla-first i18n, charts |
| Bangla font | Hind Siliguri, self-hosted through `@fontsource/hind-siliguri` (OFL-1.1), Bengali and Latin subsets | P1 | NFR-09; no request to a third-party font CDN (privacy, low bandwidth); the memo (P7) embeds the same files |
| Frontend serving | nginx container behind Caddy | P11 | Playbook P11 |
| Reverse proxy | Caddy 2 with automatic HTTPS | P11 | Playbook P11 |
| Automation | n8n (pinned, basic auth) | P11 | Fixed stack; FR-84 |
| Demo tunnel | Cloudflare Tunnel, dev-only, API key required | P8, P11 | Playbook P8 and P11 budget alternative |
| Backend tests | JUnit 5, AssertJ, Spring Boot Test, Testcontainers (pgvector image), WireMock, jqwik, ArchUnit, JaCoCo | P1 | `test-strategy.md` |
| Frontend tests | Vitest, React Testing Library, MSW, Playwright, `@axe-core/playwright` | P1 (unit), P9 (e2e) | `test-strategy.md` |
| Load test | k6 | P11 | FR-87 |
| CI | GitHub Actions, Dependabot, secret scanning | P1, P11 | Repository is on GitHub |

## Version log

| Date | Component | Version | Note |
| --- | --- | --- | --- |
| 2026-11-10 | Spring Boot | 3.5.6 | Phase 1 pin |
| 2026-11-10 | Spring AI | 1.0.1 → latest 1.1.x GA (exact patch recorded at P1) | Streamable HTTP MCP server transport (C17) |
| 2026-11-10 | Spring Boot | 3.5.6 → 3.5.16 | P1: latest 3.5.x patch |
| 2026-11-10 | Spring AI BOM | 1.1.8 | P1: latest 1.1.x GA patch on Maven Central for Boot 3.5 (C17); `spring-ai-starter-model-anthropic` removed |
| 2026-11-10 | Anthropic Java SDK | `com.anthropic:anthropic-java` 3.7.0 | P1: `x-api-key` is set explicitly, because the 3.7 client otherwise sends the key as a Bearer token |
| 2026-11-10 | Gradle | 8.14.3 (wrapper unchanged) | P1: runs on JDK 21; Gradle 8.14 does not run on JDK 25 |
| 2026-11-10 | networknt json-schema-validator | 1.5.9 | P1: last line on Jackson 2 |
| 2026-11-10 | springdoc-openapi | 2.8.17 | P1: OpenAPI 3.0 output |
| 2026-11-10 | Test libraries | ArchUnit 1.4.1, WireMock 3.13.2, Testcontainers 1.21.4 (Boot-managed), JaCoCo 0.8.13 | P1 |
| 2026-11-10 | Container images | `pgvector/pgvector:0.8.7-pg17`, `ollama/ollama:0.40.16` | P1: pinned tags (C8); Testcontainers uses the same pgvector tag |
| 2026-11-11 | jsoup, PDFBox | removed from `build.gradle` | P1 review fix 8: no P1 code uses them; P3 adds jsoup and P5 adds PDFBox, each at its latest patch, with the code that needs them. The table above is unchanged |
| 2026-11-11 | jsoup, PDFBox, jqwik | 1.24.1, 3.0.8, 1.9.3 | P3 (ADR-0011): latest on Maven Central; PDFBox arrives at P3 because FR-12 requires the PDF fallback |
| 2026-11-10 | Frontend | TypeScript 5.9 (MSW 3 needs ≥ 5.9), Vitest 5, MSW 3, ESLint 10 with typescript-eslint strict, Prettier 3, i18next 27, TanStack Query 5, openapi-typescript 7, Node 24 | P1 |

## Consequences

Positive:

- Every requirement has a named component; no hidden infrastructure.
- Only Gotenberg and Caddy are added beyond the brief's containers, both stateless.
- The Claude integration uses the current API surface instead of waiting for framework support.

Negative:

- Two ways of calling models (Anthropic SDK and Spring AI) must stay behind `ChatPort`; `ArchitectureTest` enforces the confinement.
- Tesseract native libraries enlarge the backend image.
- Gotenberg adds one container and a network hop on memo download (outside the 60-second analysis path).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Spring AI Anthropic module for Claude | The Phase 1 version predates structured outputs and `effort`, and it sends `temperature`, which current Claude models reject |
| Gemini through Spring AI's Google GenAI module or the OpenAI-compatible endpoint | Ties an optional provider to a module version or a compatibility shim; a small `RestClient` adapter is simpler to test |
| Cloud OCR or a cloud vision model | Sends unredacted PII to a third party (R-03) |
| Browser print-to-PDF as the only memo format | Output differs across phones and is hard to test in CI; kept as a fallback |
| Server sessions (Spring Session JDBC) | Playbook P2 specifies stateless JWT; sessions would add a table and sticky state |
| JPA/Hibernate | Little value for a mostly read-only corpus queried with vector, text and graph SQL |

## Verification

- `ArchitectureTest` (SDK confinement) passes.
- CI check: no unpinned image tags in compose files (from P1).
- `MemoPdfIT` passes on 20 Bangla conjunct strings (FR-55).
- `McpServerIT` connects over Streamable HTTP with the official MCP Java SDK client (FR-64).
