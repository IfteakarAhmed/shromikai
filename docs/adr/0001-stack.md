# ADR-0001: Technology stack

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | ADR-0003 (privacy), ADR-0004 (database), ADR-0005 (model routing); playbook P1 to P11 |

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
| OpenAPI | springdoc-openapi (`/swagger-ui`, `/v3/api-docs`) | P7 | Playbook P7; generated frontend types |
| MCP server | Spring AI MCP server starter (WebMVC); official MCP Java SDK client in tests | P8 | Playbook P8 |
| Metrics | Micrometer with the Prometheus registry | P1, P10 | NFR-18 |
| Formatting and lint | Spotless with google-java-format; ESLint and Prettier | P1 | Playbook P1 |
| Frontend runtime | React Router, TanStack Query, i18next, Recharts | P1, P9 | Routing, server state without persistence, Bangla-first i18n, charts |
| Frontend serving | nginx container behind Caddy | P11 | Playbook P11 |
| Reverse proxy | Caddy 2 with automatic HTTPS | P11 | Playbook P11 |
| Automation | n8n (pinned, basic auth) | P11 | Fixed stack; FR-84 |
| Demo tunnel | Cloudflare Tunnel, dev-only, API key required | P8, P11 | Playbook P8 and P11 budget alternative |
| Backend tests | JUnit 5, AssertJ, Spring Boot Test, Testcontainers (pgvector image), WireMock, jqwik, ArchUnit, JaCoCo | P1 | `test-strategy.md` |
| Frontend tests | Vitest, React Testing Library, MSW, Playwright, `@axe-core/playwright` | P1 (unit), P9 (e2e) | `test-strategy.md` |
| Load test | k6 | P11 | FR-87 |
| CI | GitHub Actions, Dependabot, secret scanning | P1, P11 | Repository is on GitHub |

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
