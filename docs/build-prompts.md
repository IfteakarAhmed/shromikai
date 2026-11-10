# Build prompts (master prompts)

These are the prompts the team gives Claude, one per phase, in order. Each prompt is self-contained.
They are also our development prompt library for the submission.

How to use: copy one prompt, send it to Claude, run the acceptance check on your machine, report the result, then move to the next.

Priority key: **[MUST]** needed for a working demo · **[POINTS]** high score per effort on the submission form · **[BONUS]** only if time allows.

---

## Phase 1 — Foundation ✅ done

Set up the ShromikAI repository: Gradle Spring Boot 3.5 backend with YAML config, Flyway schema for versioned law sections, section graph, trace and review queue, docker-compose with pgvector and Ollama, and a React + TypeScript + Vite + Tailwind frontend shell that shows backend status.

**Acceptance:** backend starts, `GET /api/v1/meta` returns JSON, frontend shows "চালু".

---

## Phase 2 — Login and judge test accounts [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 2: authentication.

Goal: judges must log in with test accounts (the submission form requires test credentials).

Build:
- Spring Security with stateless JWT. Package bd.shromikai.auth.
- Roles: WORKER, HR, ADMIN. Flyway migration for app_user (email, bcrypt password hash, role, display name).
- Endpoints: POST /api/v1/auth/login, GET /api/v1/auth/me.
- Seed three demo accounts on startup only when the profile "demo" is active: worker@shromikai.demo, hr@shromikai.demo, admin@shromikai.demo. Passwords come from environment variables, never hard-coded.
- /api/v1/meta and /actuator/health stay public. Everything else requires login. Review queue endpoints are ADMIN only.
- Frontend: login page in Bangla, token kept in memory with a refresh on reload, logout button, role shown in the header.
- Tests for login success, wrong password, and role-based access.

Acceptance: I can log in as each demo account in the browser; a WORKER gets 403 on the review queue.
Update docs/dev-log.md and commit.
```

---

## Phase 3 — Law corpus [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 3: the law corpus.

Build a law ingestion pipeline in package bd.shromikai.law:
- Jsoup scraper for the official Laws of Bangladesh portal (bdlaws.minlaw.gov.bd). Polite: 1 request per second, a clear user agent, cache raw HTML under data/raw/ (gitignored).
- Acts: Bangladesh Labour Act 2006 (with amendments) and Contract Act 1872. Store each section with section number, heading, text in English and Bangla where available, version label, effective date, is_current and source URL.
- If a page cannot be scraped, fall back to parsing the official PDF with PDFBox.
- Detect cross-references ("section 2", "ধারা ২৬") and definition use; write REFERS_TO and USES_DEFINITION edges.
- A data quality report: count of sections per act, empty sections, sections without source URL, duplicate numbers. Fail the run if any section has no source URL.
- Admin endpoint POST /api/v1/admin/laws/ingest (ADMIN only) and a CLI runner.
- Also load 3 ILO conventions (working hours, forced labour, holidays with pay) as jurisdiction "ILO".

Acceptance: the quality report shows both acts loaded; I check 5 random sections against the official site and they match.
Update docs/dev-log.md and commit.
```

---

## Phase 4 — Retrieval [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 4: retrieval in package bd.shromikai.retrieval.

- Embed every current section with bge-m3 via Ollama (Spring AI EmbeddingModel). Batch the calls; skip sections whose text has not changed (content hash).
- Hybrid search: pgvector cosine top 20 + Postgres full-text top 20, merged with reciprocal rank fusion, top 8 returned.
- Graph expansion: add the definitions and referenced sections of the top results (max 4 extra).
- Query rewriting: a contract clause is rewritten into a short legal search query by a cheap model before search.
- Only is_current sections are searchable.
- GET /api/v1/laws/search?q= for debugging.
- A retrieval test set eval/retrieval.jsonl with 20 questions and the expected section numbers, and a runner that reports hit@5.

Acceptance: hit@5 is printed for the 20 questions and most return the right section.
Update docs/dev-log.md and commit.
```

---

## Phase 5 — Contract intake and local privacy [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 5: contract intake in package bd.shromikai.ingest.

- Accept PDF, image (JPG/PNG) or plain text, max 10 MB.
- PDF text with PDFBox; images and scanned PDFs with Tesseract OCR (Bangla + English).
- Local PII redaction BEFORE any cloud call: regex for NID, phone, email, bank account, amounts; plus a local Qwen 2.5 model through Ollama to find person and company names. Replace with placeholders like [NAME_1]. Keep the mapping in memory only so the final memo can put names back for the user.
- Split the contract into clauses and classify each (wages, working_hours, overtime, leave, probation, notice, termination, benefits, other) with a cheap Claude model (Haiku) in JSON mode.
- Nothing from the contract is written to disk, database or logs.

Acceptance: 5 sample contracts (in eval/contracts/, synthetic) are split correctly; a log check shows no names or NIDs were sent to the cloud model.
Update docs/dev-log.md and commit.
```

---

## Phase 6 — Risk analysis and citation verifier ⭐ [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 6, the core: packages analysis, rules, verify.

1. Rule engine (non-AI): deterministic checks for numeric limits in the Labour Act (for example working hours, overtime, leave days, notice periods). Each rule stores the act, section and the exact quoted text it relies on, loaded from the corpus, not hard-coded numbers alone.
2. Risk agent: for each clause, Claude Sonnet receives the clause, the user's side (worker or employer) and the retrieved sections. It returns risks in JSON. It may cite ONLY sections from the retrieved list (section ids given in the prompt).
3. Citation verifier, per citation:
   a. Existence: act and section exist and are current.
   b. Quote match: the quoted text appears in the section after normalising Bangla Unicode, spaces and Bangla digits.
   c. Support: an independent model (Gemini; fall back to a separate Claude prompt if no key) answers whether the section supports the claim.
   Status: VERIFIED, WEAK or UNCERTAIN.
4. Corrective retrieval: if a citation fails a or b, rewrite the query, retrieve again and retry once. Still failing: drop the citation, mark the risk UNCERTAIN and create a review_item.
5. Write a trace row per step (section ids, statuses, timings, token counts), never contract text.
6. Prompts in backend/src/main/resources/prompts/ and docs/prompts/.

Acceptance: a sample contract returns at least 3 risks with VERIFIED citations; a test that injects a fake section number proves the verifier catches it.
Update docs/dev-log.md and commit.
```

---

## Phase 7 — Memo, API and cost control [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 7.

- Bangla memo: plain explanation per risk, suggested revised clause, obligations checklist for both sides, the disclaimer. Names restored from the in-memory placeholder map.
- PDF export of the memo.
- REST API: POST /api/v1/analyses (multipart file or text + side), GET /api/v1/analyses/{id}/trace, POST /api/v1/compare (plain LLM vs ShromikAI on the same contract), GET /api/v1/review-items (ADMIN).
- OpenAPI docs with springdoc at /swagger-ui.
- Cost control: Anthropic prompt caching on system prompts and law context, cheap-model routing (Haiku for classification and rewriting, Sonnet for risk analysis), cache identical retrieval queries, rate limit per user.
- Record tokens and cost per analysis in the trace.

Acceptance: one API call with a sample contract returns a full Bangla memo; the PDF downloads; swagger lists all endpoints.
Update docs/dev-log.md and commit.
```

---

## Phase 8 — MCP server [POINTS: 20 on the form]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 8: an MCP server in package bd.shromikai.mcp using the Spring AI MCP server starter (Streamable HTTP transport).

Tools:
- search_law(query, top_k): hybrid retrieval, returns sections with source URLs.
- verify_citation(act, section, quote, claim): runs the 3-check verifier.
- analyze_contract(text, side): full pipeline, returns risks and statuses.

Protect it with an API key header. Write docs/mcp.md with setup steps for Claude Desktop and an example session.
Expose it for the demo with Cloudflare Tunnel and document the security posture.

Acceptance: from Claude Desktop I can call all three tools against my local server.
Update docs/dev-log.md and commit.
```

---

## Phase 9 — Web UI [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 9: the full React UI.

Screens (Bangla, mobile-first):
1. Upload: file or camera photo, choose side (worker or employer).
2. Results: risk cards with severity, verification badge (verified, weak, uncertain), plain-Bangla reason, and a "কেন?" panel showing the trace.
3. Memo: revised clauses, checklist, PDF download.
4. Compare mode: plain LLM and ShromikAI side by side, wrong citations highlighted in red.
5. Review queue (ADMIN).
6. /docs: a live page with architecture diagram, evaluation charts (Recharts), prompt library, data lifecycle and team.

Our designer prototypes screens in Lovable or v0 first; port the designs into this codebase.

Acceptance: the full demo flow works in the browser on a phone-size screen.
Update docs/dev-log.md and commit.
```

---

## Phase 10 — Evaluation [MUST]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 10: evaluation in eval/ and package bd.shromikai.eval.

- Test set: 10–15 contracts (synthetic and anonymised templates, labelled), each with expected risks and correct sections, in the structure from docs/architecture.md. A lawyer reviews the labels; record reviewer and date.
- Runner compares baseline (same model, plain prompt, no retrieval or verifier) vs ShromikAI.
- Metrics: citation accuracy, hallucinated citations caught, risk recall, correct abstention, Bangla vs English consistency, worker vs employer balance, time and cost per contract.
- LLM-as-judge for explanation quality, checked against the human labels.
- Output eval/results/report.md and JSON for the /docs charts. Report the number of cases and the failures honestly.

Acceptance: report.md exists with a baseline vs ShromikAI table produced by the runner.
Update docs/dev-log.md and commit.
```

---

## Phase 11 — Automation and deploy [MUST deploy, BONUS n8n]

```
Read AGENTS.md and docs/architecture.md. Implement Phase 11.

- n8n (self-hosted in docker-compose): a weekly workflow that checks the official law pages for changes (hash compare) and calls the ADMIN ingest endpoint when something changed, then notifies the team.
- Production docker-compose with the backend, frontend (nginx), Postgres and Ollama.
- Deploy to a cloud VM; HTTPS; demo profile with judge accounts.
- Final docs: README, docs/submission-map.md filled with real values.

Acceptance: a public URL works and judges can log in.
Update docs/dev-log.md and commit.
```
