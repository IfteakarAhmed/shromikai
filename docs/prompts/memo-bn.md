# `memo-bn` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/memo-bn/v1.md` |
| Output schema | `schemas/llm/memo-bn.v1.schema.json` |
| Role | `REASONING` (Claude Sonnet, effort low) |
| Phase | P7 (FR-51, FR-56) |
| Eval | set `clarity`, metric M-21 |

## Purpose

Writes only two parts of the memo, the summary and the next steps, in plain Bangla at about grade-8 reading level. Every other memo section (risk table, per-risk explanation, citation chips, revised clauses, obligations, verification and method, disclaimer) is assembled by `MemoComposer` without a model call.

## Where it runs

Step `COMPOSE`, after the counter-offers. One call per analysis; the prompt used follows the output language.

## Input and output

- `side`, `reading_level` (`BASIC` or `STANDARD`), `profile`, `counts` (trusted); `findings` (untrusted, inside `<contract_clause>`: redacted paraphrases, never contract text).
- Output: `{ status, reason, summary (at most 600 characters), next_steps (at most 5, each at most 140) }`.
- Bounded so the answer always fits: `PromptAnswerBoundAuditTest` builds the largest valid answer and checks it against the 2,000-token cap with the 15% margin and at harsher rates.

## Failure handling

Invalid output, a refusal or an outage: the memo uses a fixed summary and fixed next steps built without a model, and the result carries warning `memo-narrative-unavailable` (ADR-0020). An egress block still fails the job (R-03).

## Safety

The prompt declares the finding text untrusted. It forbids citing law from memory and section numbers in the narrative, so the only citations in the memo are the verified chips (R-01).

## Changelog

- v1 (2026-11-12): initial version; status `experimental` until its dev-split report is attached.
