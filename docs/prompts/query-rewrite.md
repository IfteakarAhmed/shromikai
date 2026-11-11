# `query-rewrite` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/query-rewrite/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/query-rewrite.v1.schema.json` |
| Role | `FAST` (Claude Haiku, effort low) |
| Phase | P4 (FR-21) |
| Eval | set `retrieval`, metric M-15 (`eval/reports/retrieval.md`, ablation row "hybrid + graph + rewrite") |

## Purpose

Statute text and contract clauses differ in vocabulary and often in language. This prompt turns one
**redacted** clause into one to three short search queries in Bangla and English, so hybrid
retrieval can find the sections the clause is about.

## Where it runs

- Inside an analysis (step `RETRIEVE`) and in the corrective loop, on redacted clauses only.
- **Never** for free-text law search (`GET /api/v1/laws/search`, MCP `search_bd_law`): a query may
  contain personal data, so that path stays local (ADR-0006).

## Input and output

- `clause` (untrusted): the redacted clause, XML-escaped inside `<contract_clause>` in the user turn.
- Output: `{ "status": "OK" | "ABSTAIN", "reason": "NONE" | "NOT_A_CONTRACT_CLAUSE" | "UNREADABLE", "queries": [string] }`.
  The schema keeps to the features structured output supports; the code keeps at most three
  non-blank queries and treats `ABSTAIN` or an empty list as "no rewrite".

## Failure handling

Invalid JSON: one repair attempt through `ModelGateway` with the validator errors; still invalid, a
refusal, a timeout, or an egress block: the caller falls back to the raw clause and retrieval
continues. Rewriting can only add queries, never remove the original behaviour.

## Safety

No tools. The prompt asks for topics only; it never cites law, so it cannot introduce an invented
section (R-01). The abstention element covers non-contract and unreadable input.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
