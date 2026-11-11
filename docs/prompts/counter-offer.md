# `counter-offer` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/counter-offer/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/counter-offer.v1.schema.json` |
| Role | `REASONING` (Claude Sonnet, effort low) |
| Phase | P6 (FR-41) |
| Eval | set `contracts`, metric M-27 |

## Purpose

Proposes a revised clause that resolves a verified risk and stays commercially reasonable for both parties.

## Where it runs

- Step `COMPOSE`, one call per finding of kind `CONFLICT`, `AMBIGUITY` or `OMISSION` whose final status is `VERIFIED`; never for an `UNCERTAIN` finding. The proposal's facts are re-extracted (`clause-facts`) and checked by the rule engine; a violating proposal is regenerated once with `previous_attempt_problem`, then dropped with warning `counter-offer-dropped`.

## Input and output

- `side`, `output_language`, `finding` (with verified citations), `limits`, `previous_attempt_problem` (trusted).
- `clause` (untrusted): the redacted clause to revise.
- Output: `{ status, reason, action: REPLACE or INSERT, proposed_text, rationale }`.

## Failure handling

Invalid output after one repair, `ABSTAIN`, or a second rule violation drops the redline with warning `counter-offer-dropped`; the analysis continues.

## Safety

The prompt declares the clause untrusted and keeps placeholders as they are; a proposal is never shown unless the rule engine passes it.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
