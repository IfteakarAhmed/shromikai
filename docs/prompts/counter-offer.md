# `counter-offer` (v1 and v2, experimental)

| | |
| --- | --- |
| Runtime files | `backend/src/main/resources/prompts/counter-offer/v1.md` (kept, unused) and `v2.md` (used) |
| Output schemas | `schemas/llm/counter-offer.v1.schema.json` and `counter-offer.v2.schema.json` |
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

## Version 2 (the one the pipeline uses)

In the first full live run three of nine v1 answers stopped at the 500-token cap (a Bangla rationale
of up to 600 characters) and their redlines were dropped. v1 is frozen and kept; v2 bounds the
answer (ADR-0017):

- `max_output_tokens` 900, `timeout_seconds` 45 (v1: 500 and 30).
- `proposed_text` at most **600 characters**, `rationale` one plain sentence of at most **140 characters**. The limits are in the schema; `CounterOfferAnswerSizeTest` builds the largest answer and checks it against the cap with a margin (about 725 of 900 tokens at measured rates).
- Consequence to watch: a clause longer than 600 characters cannot be proposed in full and its redline is dropped; M-27 shows how often.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
- v2 (2026-11-11): bounded output and a 900-token cap after the v1 cap failure above; status `experimental` until its dev-split report is attached.
