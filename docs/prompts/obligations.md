# `obligations` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/obligations/v1.md` |
| Output schema | `schemas/llm/obligations.v1.schema.json` |
| Role | `FAST` (Claude Haiku, effort low) |
| Phase | P7 (FR-53) |
| Eval | set `contracts`, metric M-28 |

## Purpose

A checklist of what the employer and the worker must do, from the redacted clauses and the
candidate statute sections: party, action, trigger, deadline, source (contract, statute or both),
status and, for statutory items, one claimed citation.

## Where it runs

Step `RISK`, in parallel with the risk batches (one call for the whole contract). Its claimed
citations are restricted to the analysis's candidate section keys and are verified in `VERIFY`; a
`STATUTE` or `BOTH` item without a verified citation is dropped (`ObligationsExportIT`).

## Input and output

- `output_language`, `candidate_sections` (trusted); `clauses` (untrusted, inside `<contract_clause>`).
- Output: `{ status, reason, items: [{ party, obligation, trigger, deadline, source, status, clause_ids, citations }] }`.
- Bounded: at most 6 items, one citation per item, short texts. `PromptAnswerBoundAuditTest` checks the worst case against the 3,500-token cap (architecture §5.3 planned 1,500; see ADR-0020).
- `dynamic_enums`: `$.items[*].citations[*].section_key` is restricted to the candidate keys.

## Failure handling

Invalid output after one repair or a refusal: the checklist is omitted with warning `obligations-unavailable`; the findings and the memo are unaffected. An egress block still fails the job (R-03).

## Safety

Clause text is untrusted and escaped. The model cites only supplied sections; the verifier decides what a user sees (R-01).

## Changelog

- v1 (2026-11-16): initial version; status `experimental` until its dev-split report is attached.
