# `clause-classify` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/clause-classify/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/clause-classify.v1.schema.json` |
| Role | `FAST` (Claude Haiku, effort low) |
| Phase | P5 (FR-33, FR-34) |
| Eval | set `contracts`, metric M-23 |

## Purpose

Document-type gate and clause type with confidence for each redacted clause.

## Where it runs

- Step `CLASSIFY`, on **redacted** clauses only, after the egress guard, in batches.

## Input and output

- `clauses` (untrusted): redacted clauses `{ id, text }`, escaped inside `<contract_clause>`.
- Output: `{ status, reason, document_type, clauses: [{ id, type, confidence }] }`. `type` is the `ClauseType` enum of `docs/spec/api.md` §5.1. The first batch decides the document type; anything other than `EMPLOYMENT_CONTRACT` fails with `not-an-employment-contract`.

## Failure handling

Invalid output after one repair: `model-output-invalid`. Refusal or provider outage: `model-refused` or `upstream-unavailable`. An egress block: `privacy-guard-blocked`.

## Safety

The system prompt declares clause text untrusted and tells the model to classify an instruction-like clause by its topic. Flagged clauses are analysed as data (FR-34).

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
