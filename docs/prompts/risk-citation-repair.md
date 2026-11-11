# `risk-citation-repair` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/risk-citation-repair/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/risk-citation-repair.v1.schema.json` |
| Role | `REASONING` (Claude Sonnet, effort medium) |
| Phase | P6 (FR-46) |
| Eval | set `contracts`, metrics M-02, M-04 |

## Purpose

One corrective attempt for a finding whose citation was not verified: choose a supporting section from an extended candidate list, or abstain.

## Where it runs

- Step `CORRECT`, after a re-retrieval from a rewritten query. Repairs for different findings run in parallel. There is no second attempt.

## Input and output

- `finding`, `reason_codes`, `candidate_sections` (trusted).
- `clauses` (untrusted): the redacted clauses of the finding.
- Output: `{ status, reason, citations: [] or one { section_key, quote, claim } }`. `$.citations[*].section_key` is restricted per request to the extended candidate keys.

## Failure handling

Invalid output after one repair is treated as `ABSTAIN`: the finding stays `UNCERTAIN` without a citation and gets a review item.

## Safety

The prompt tells the model that abstaining is a correct outcome and forbids citing from memory.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
