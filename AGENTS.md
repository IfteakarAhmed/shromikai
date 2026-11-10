# AGENTS.md — rules for AI coding agents working on ShromikAI

ShromikAI analyses Bangladeshi employment contracts and cites only verified sections of law.
Read `docs/architecture.md` before changing code. Build work follows the phases in `docs/build-prompts.md`.

## Non-negotiable rules

1. **No invented law.** Code must never produce a citation that did not pass the verifier. If a source cannot be verified, the output is "uncertain" plus human review.
2. **No contract text at rest.** Contract text is processed in memory only. Never write it to the database, logs, traces or test fixtures committed to git.
3. **PII first.** Personal data is redacted locally before any cloud LLM call.
4. **Structured outputs.** Every LLM call returns JSON matching a schema that is validated in code. Temperature 0.
5. **Prompts are files.** Runtime prompts live in `backend/src/main/resources/prompts/` and are mirrored with docs in `docs/prompts/`. Never inline a prompt in Java code.
6. **No fabricated results.** Evaluation numbers come only from running `eval/`. Never hand-edit results.

## Stack and conventions

- Backend: Java 21, Spring Boot 3.5, Spring AI, Gradle (`backend/build.gradle`), config in `application.yml`.
- Packages under `bd.shromikai`: `auth`, `law`, `retrieval`, `ingest`, `analysis`, `rules`, `verify`, `memo`, `trace`, `api`, `mcp`, `eval`.
- Database changes only through new Flyway migrations (`V{n}__name.sql`); never edit an applied migration.
- Frontend: React 19 + TypeScript + Vite + Tailwind in `frontend/`. Bangla UI text, Hind Siliguri font.
- Every phase ends with: tests passing, the phase's acceptance check documented, `docs/dev-log.md` updated, one commit.

## Review gate per phase

A phase is done only when the human team member has run the acceptance check on their own machine and confirmed it.
