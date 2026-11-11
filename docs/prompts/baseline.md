# `baseline` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/baseline/v1.md` |
| Output schema | `schemas/llm/baseline.v1.schema.json` |
| Role | `REASONING` (Claude Sonnet, effort medium) |
| Phase | P7 (FR-59) |
| Eval | set `contracts`, metrics M-03 and M-04 |

## Purpose

The plain-LLM baseline for compare mode: the same model class reads the redacted contract with no
retrieval and no verifier and lists risks with the sections it believes apply. Its citations are
then checked by the verifier and marked (`VERIFIED` or `REJECTED` with the reason codes); a
fabricated section shows as `REJECTED` with `SECTION_NOT_FOUND`.

## Where it runs

`POST /api/v1/compare` only, beside the full analysis. It counts as part of the 2 analyses a compare
costs for quotas.

## Input and output

- `side`, `output_language` (trusted); `document` (untrusted, inside `<contract_document>`, redacted).
- Output: `{ status, reason, findings: [{ title, severity, explanation, citations: [{ section_key, quote, claim }] }] }`, at most 4 findings and 2 citations each.
- No `dynamic_enums`: the baseline has no candidate set by design, so any key it writes is checked against the corpus only.

## Failure handling

Invalid output after one repair, a refusal or an outage: the compare response shows the baseline as unavailable with the reason; the ShromikAI side is unaffected.

## Safety

Document text is untrusted and escaped. Citations are claims only (origin `BASELINE`); nothing from the baseline reaches a user without a verifier status next to it (R-01).

## Changelog

- v1 (2026-11-16): initial version; status `experimental` until its dev-split report is attached.
