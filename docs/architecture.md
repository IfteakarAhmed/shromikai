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

| Phase | Deliverable | Done when |
| --- | --- | --- |
| 1 | Repo, schema, compose, frontend shell | App starts, `/api/v1/meta` works |
| 2 | Law corpus | 5 random sections match the official site |
| 3 | Retrieval | Correct section in top 5 for most of 20 test questions |
| 4 | Contract ingest | 5 sample contracts split correctly, no PII sent to LLM |
| 5 | Risk agent + verifier | ≥3 risks found; planted fake citation is caught |
| 6 | Memo + API | One API call returns a full Bangla memo |
| 7 | UI | Full demo flow works in the browser |
| 8 | Evaluation | Baseline vs ShromikAI results table |
| 9 | Deploy | Public URL works |
