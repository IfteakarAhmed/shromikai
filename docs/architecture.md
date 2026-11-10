# Architecture

Every legal citation must pass verification before it reaches the user. The rest of the system is a reliable pipeline; agents are used only where real reasoning is needed (risk analysis and verification).

## Pipeline

1. **Ingest** — PDF, image or text → text; personal data (names, NID, phone, address, salary amounts) replaced with placeholders before any LLM call.
2. **Classify** — split the contract into clauses and tag each (wages, working hours, leave, notice, probation, overtime, termination…).
3. **Retrieve** — hybrid search (pgvector + keyword) over current law sections, then graph expansion for definitions and cross-references.
4. **Risk agent** — finds risks per clause; may only cite sections from the retrieved list.
5. **Citation verifier** — three checks per citation:
   - *Existence*: the act and section exist and are the current version.
   - *Quote match*: the quoted text appears in the official section (after Bangla Unicode, whitespace and digit normalisation).
   - *Support*: an independent prompt checks that the section supports the claim.
   Result: **verified**, **weak**, or **uncertain** (one retry, then human review without a citation).
6. **Memo** — plain-Bangla explanation, suggested clause revisions, obligations checklist.
7. **UI** — risk cards with verification badges and a "why?" trace.

## Knowledge base

- `law_act`, `law_section` — one row per section version; only `is_current` rows are cited.
- `section_edge` — graph: `USES_DEFINITION`, `REFERS_TO`, `AMENDED_BY`.
- Source: official Laws of Bangladesh portal (bdlaws.minlaw.gov.bd), with source URL per section.

## Privacy

- Contracts are processed in memory and never stored by default.
- `analysis_trace` and `review_item` store only section ids, results and redacted summaries — never contract text.

## Build phases

Full prompts and acceptance checks: [build-prompts.md](build-prompts.md). Form mapping: [submission-map.md](submission-map.md).

| Phase | Deliverable | Priority |
| --- | --- | --- |
| 1 | Repo, schema, compose, frontend shell ✅ | must |
| 2 | Login, roles, judge test accounts | must |
| 3 | Law corpus with versions and graph | must |
| 4 | Hybrid retrieval, graph expansion, query rewriting | must |
| 5 | Contract intake, OCR, local PII redaction | must |
| 6 | Rule engine, risk agent, 3-check verifier, corrective retry | must |
| 7 | Bangla memo, PDF, REST API, cost control | must |
| 8 | MCP server | high points |
| 9 | Web UI + live /docs page | must |
| 10 | Evaluation: baseline vs ShromikAI | must |
| 11 | n8n automation, deploy | must (n8n bonus) |
