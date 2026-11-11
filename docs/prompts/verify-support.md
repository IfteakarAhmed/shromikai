# `verify-support` (v2, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/verify-support/v1.md` (v1), `backend/src/main/resources/prompts/verify-support/v2.md` (v2, latest) |
| Output schema | `backend/src/main/resources/schemas/llm/verify-support.v1.schema.json` (v1), `backend/src/main/resources/schemas/llm/verify-support.v2.schema.json` (v2, latest) |
| Role | `VERIFIER` (Gemini Flash, temperature 0) |
| Phase | P6 (FR-44, FR-45) |
| Eval | set `citations`, metrics M-04, M-05 |

## Purpose

Check (c) of the citation verifier (architecture §7.3): does the text of one statute section support
a stated claim? It uses an independent model family.

## Where it runs

- Step `VERIFY` and again in `CORRECT`, only for a citation whose existence and quote checks passed.

## Input and output

- `claim` and `quote` (untrusted): produced by another model, escaped inside `<contract_clause>`. They never hold a contract clause.
- `section_text` and `translation_text` (trusted): from the statute corpus.
- Output: `{ label, confidence, reason }` with `label` one of `SUPPORTS`, `PARTIAL`, `CONTRADICTS`, `UNRELATED`. `SUPPORTS` with confidence of at least 0.70 passes (architecture §7.4).

## Failure handling

Invalid output after one repair counts as `UNRELATED`. A provider failure, timeout or refusal on the primary verifier falls back to `verify-support-claude`; if that fails too, the citation is `UNCERTAIN` (`VERIFIER_UNAVAILABLE`), never `VERIFIED`.

## Safety

The system prompt declares the claim and quote untrusted and forbids using any law that is not in the supplied text. The verifier never receives contract text (`VerifierInputTest`).

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its report on the `citations` set is attached.
- v2 (2026-11-11): the one-sentence reason of at most 200 characters is now a schema limit and the output cap is 250 tokens (was 150), so the largest allowed answer (about 182 tokens at measured rates) fits; wording unchanged. v1 is kept as it was (ADR-0019).
