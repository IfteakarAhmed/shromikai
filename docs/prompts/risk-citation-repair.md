# `risk-citation-repair` (v2, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/risk-citation-repair/v1.md` (v1), `backend/src/main/resources/prompts/risk-citation-repair/v2.md` (v2, latest) |
| Output schema | `backend/src/main/resources/schemas/llm/risk-citation-repair.v1.schema.json` (v1), `backend/src/main/resources/schemas/llm/risk-citation-repair.v2.schema.json` (v2, latest) |
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

## Output bound (v2)

The schema allows one citation because two Bangla citations of maximum size (about 512 tokens at measured rates) would leave no 15% margin under the 600-token cap (`RepairAnswerSizeTest`). A repair needs one verified citation; if it cannot find one it abstains.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
- v2 (2026-11-11): bounded output after a live v1 call stopped at its 300-token cap: at most one citation, quote 20 to 120 characters, claim 20 to 100 characters; output cap 600 tokens. The largest allowed answer is about 300 tokens at measured rates (`RepairAnswerSizeTest`, `PromptAnswerBoundAuditTest`). v1 is kept as it was (ADR-0019).
