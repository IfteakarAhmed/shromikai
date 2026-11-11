# Prompt library

Every prompt ShromikAI uses at runtime is a versioned file, explained for humans in this folder.
This page is the index for the team and for the submission form's "Prompt Library" section.

- **Runtime files:** `backend/src/main/resources/prompts/{id}/v{n}.md`
- **Output schemas:** `backend/src/main/resources/schemas/llm/{id}.v{n}.schema.json`
- **Explanations:** `docs/prompts/{id}.md` (one per prompt; required by `promptLint`)
- **Standard:** [AGENTS.md §5](../../AGENTS.md#5-runtime-prompt-standard) (front matter, `<contract_clause>` isolation, abstention block, lint rules)
- **Lock file:** `backend/src/main/resources/prompts/LOCK`; released versions never change, a change creates a new version
- **Model routing:** [ADR-0005](../adr/0005-model-routing.md)
- **Build prompts** (how the product itself was built): [master prompt playbook](../playbook/master-prompt-playbook.md)

## Catalogue

| Prompt | Model role | Model | Purpose | Eval set and metrics | Status |
| --- | --- | --- | --- | --- | --- |
| `query-rewrite` | FAST | Claude Haiku 5.5 | 1 to 3 short legal search queries in Bangla and English per redacted clause or failed finding (never for free-text law search) | `retrieval`: M-15 | Experimental v1 (P4); lock after its dev-split report |
| `pii-extract` | LOCAL_PII | qwen2.5:7b-instruct (local) | Persons, organisations, addresses and employee IDs in a text chunk | `pii`: M-25 | Experimental v1 (P5); lock after its dev-split report |
| `clause-segment` | FAST | Claude Haiku 5.5 | Fallback segmentation for unnumbered text | `contracts`: M-23 | Experimental v1 (P5); lock after its dev-split report |
| `clause-classify` | FAST | Claude Haiku 5.5 | Document-type gate; clause type with confidence | `contracts`: M-23 | Experimental v1 (P5); lock after its dev-split report |
| `clause-facts` | FAST | Claude Haiku 5.5 | Typed facts per clause, cross-checked by regex | `contracts`: M-22 | Experimental (P6) |
| `risk-analysis` | REASONING | Claude Sonnet 5.5 | Risk agent (opposing counsel for the user's side), up to 4 clause batches in parallel: findings with severity, non-obvious flag and citations restricted to retrieved sections | `contracts`: M-06 to M-10 | Experimental (P6) |
| `risk-citation-repair` | REASONING | Claude Sonnet 5.5 | One corrective attempt with new candidates for a citation that failed verification | `contracts`: M-11, M-12 | Experimental (P6) |
| `counter-offer` | REASONING | Claude Sonnet 5.5 | Revised clause that resolves the risk and stays reasonable for both sides; runs after verification, one call per `VERIFIED` finding | `contracts`: M-27 | Experimental (P6) |
| `verify-support` | VERIFIER | Gemini Flash | Independent judgement: SUPPORTS, PARTIAL, CONTRADICTS or UNRELATED | `citations`: M-04, M-05 | Experimental (P6) |
| `verify-support-claude` | VERIFIER_FALLBACK | Claude Haiku 5.5 | Same judgement when Gemini is unavailable (flagged `SAME_FAMILY`) | `citations`: M-04, M-05 | Experimental (P6) |
| `obligations` | FAST | Claude Haiku 5.5 | Obligations checklist for employer and worker; runs in `RISK`, citations restricted to candidates | `contracts`: M-28 | Planned (P7) |
| `memo-bn` | REASONING | Claude Sonnet 5.5 | Plain-Bangla summary and next-steps narrative of the memo (about grade-8 reading level) | `clarity`: M-21 | Planned (P7) |
| `memo-en` | REASONING | Claude Sonnet 5.5 | English summary and next-steps narrative of the memo | `contracts`: M-21 (English raters) | Planned (P7) |
| `baseline` | REASONING | Claude Sonnet 5.5 | Plain-LLM baseline for compare mode and evaluation (no retrieval, no verifier) | `contracts`: M-03 | Planned (P7) |
| `explanation-judge` | JUDGE | Gemini Flash | Clarity rating; used only after calibration against human ratings | `clarity`: M-21, M-26 | Planned (P10) |

## Rules every prompt follows

1. Output is JSON that matches its schema and is validated in code; invalid output gets one repair attempt (R-04).
2. Contract-derived text appears only inside `<contract_clause>` or `<contract_document>`, escaped, and the system prompt declares it untrusted (R-06).
3. The model may cite only the statute sections it is given, and must abstain instead of guessing.
4. Claude prompts set `effort` and never a temperature (the Claude API rejects sampling parameters); local and Gemini prompts use temperature 0.
5. Few-shot examples, where used, are built from synthetic data only (at most 3).
6. Every version names the eval set that measures it, and a new version ships with a dev-split eval report.
