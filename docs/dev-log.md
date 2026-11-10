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
