# `risk-analysis` (v1 and v2, experimental)

| | |
| --- | --- |
| Runtime files | `backend/src/main/resources/prompts/risk-analysis/v1.md` (kept, unused) and `v2.md` (used) |
| Output schemas | `schemas/llm/risk-analysis.v1.schema.json` and `risk-analysis.v2.schema.json` |
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

## Version 2 (the one the pipeline uses)

A live run on a synthetic contract showed every v1 answer stopping at its 1,400-token cap, so no
risk result was obtained (the six calls of one analysis cost US$0.29 and returned nothing). v1 is
frozen and kept; v2 bounds the answer so that it fits its cap, and the cap is higher (ADR-0016):

- `max_output_tokens` 3,000, `timeout_seconds` 90 (v1: 1,400 and 40).
- At most **3 findings** per request (the most severe, the less obvious first), at most **2 citations** per finding.
- `title` at most 50 characters, `explanation` at most 160 characters in two short sentences (it now carries what `why_it_matters` said; the field is gone), `quote` at most 120 characters, `claim` at most 100 characters. The limits are in the schema, so the worst-case answer size is known: `RiskAnswerSizeTest` builds a maximum answer and checks it against the cap with a margin.
- Consequence to watch: a batch with more than three real risks reports only three. With up to 4 batches that is at most 12 findings per contract; M-06 (risk recall) on the `dev` split shows whether that loses risks.

## Changelog

- v1 (2026-11-11): initial version; status `experimental` until its dev-split report is attached.
- v2 (2026-11-11): bounded output and a 3,000-token cap after the v1 cap failure above; status `experimental` until its dev-split report is attached.
