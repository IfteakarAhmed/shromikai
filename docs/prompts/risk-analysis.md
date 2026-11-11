# `risk-analysis` (v1, experimental)

| | |
| --- | --- |
| Runtime file | `backend/src/main/resources/prompts/risk-analysis/v1.md` |
| Output schema | `backend/src/main/resources/schemas/llm/risk-analysis.v1.schema.json` |
| Role | `REASONING` (Claude Sonnet, effort medium) |
| Phase | P6 (FR-37, FR-38, FR-39, FR-40) |
| Eval | set `contracts`, metrics M-06, M-07, M-09 |

## Purpose

Opposing counsel for the user's side: finds conflicts, omissions, ambiguities and interactions in redacted clauses and proposes citations restricted to the candidate statute sections.

## Where it runs

- Step `RISK`, up to 4 clause batches in parallel (architecture §8.5), each with an outline of the whole contract.

## Input and output

- `side`, `output_language`, `profile`, `outline`, `rule_findings`, `expected_topics_missing`, `candidate_sections` (trusted).
- `clauses` (untrusted): the redacted clauses of the batch with types and facts, escaped inside `<contract_clause>`.
- Output: `{ status, reason, findings: [{ title, kind, severity, non_obvious, clause_type, clause_ids, explanation, why_it_matters, citations: [{ section_key, quote, claim }], confidence }] }`.
- `dynamic_enums`: `$.findings[*].citations[*].section_key` is restricted per request to the batch's candidate keys (architecture §8.3), so a key outside the candidates fails schema validation.
- The severity rubric of FR-39 is in the prompt verbatim.

## Failure handling

Invalid output or a call that stops at the output cap: the batch is split in half and retried once (architecture §5.3); a second failure is `model-output-invalid`. Every citation is only a claim until the verifier accepts it (R-01).

## Safety

The prompt declares clause text untrusted. The side changes the wording of the explanation, never which statutory conflicts are reported (FR-40). Claims must not repeat seven or more consecutive words of a clause, so a claim copied from a contract is rare and caught by the 8-token check.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
