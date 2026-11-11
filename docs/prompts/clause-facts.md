# `clause-facts` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/clause-facts/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/clause-facts.v1.schema.json` |
| Role | `FAST` (Claude Haiku, effort low) |
| Phase | P6 (FR-35) |
| Eval | set `contracts`, metric M-22 |

## Purpose

Reads typed facts (parameter, number, unit, confidence) out of redacted clauses. The facts feed the rule engine; the contract's wording is not kept.

## Where it runs

- Step `RULES`, in up to 3 batches in parallel, on redacted clauses only, after the egress guard. Also run on each counter-offer proposal in `COMPOSE`, to re-check it against the rules.

## Input and output

- `clauses` (untrusted): redacted clauses `{ id, text }`, escaped inside `<contract_clause>`.
- Output: `{ status, reason, clauses: [{ id, facts: [{ parameter, value, unit, confidence }] }] }`. `parameter` and `unit` are the enums of `docs/spec/api.md` §6.3 and §6.4. A fact whose unit does not belong to its parameter is dropped in code. The regex cross-check (`FactExtractor`) lowers a fact's confidence by 0.3 when it finds a different number (FR-35).

## Failure handling

Invalid output after one repair: `model-output-invalid`. Refusal: `model-refused`. Provider outage: `upstream-unavailable`. An egress block: `privacy-guard-blocked`.

## Safety

The prompt declares clause text untrusted, tells the model to report only what the clause states and never to fill in what a law might say.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
