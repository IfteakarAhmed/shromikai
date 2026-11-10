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
