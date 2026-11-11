# ADR-0019: Answer-size audit of every prompt, and the v2 prompts it produced

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0015, ADR-0016, ADR-0017, ADR-0018; rules R-04, R-05; PRD FR-33, FR-35, FR-37, FR-41, FR-46; architecture §18.6 C44 |

## Context

Three live runs lost money to answers cut at `max_output_tokens`: `risk-analysis` v1 (US$0.29),
`counter-offer` v1 (three of nine) and, in the second full run, one of two `risk-citation-repair` v1
answers (cap 300; the schema allowed a 300-character quote and a 600-character claim). A cut answer
is invalid output, the step stops, and the call is still billed. Each time the fix was local. The
team asked for the same check on every prompt, offline, before any further live call.

## Decision

1. **A test audits the whole catalogue.** `PromptAnswerBoundAuditTest` builds, per prompt, the
   largest answer that the schema, the prompt text and the caller allow, checks it is a valid
   answer, and requires it to fit the latest version's cap at the measured token rates with a 15%
   margin and at harsher rates (0.80 and 1.00 tokens per character) with none. A prompt directory
   that is neither in the bounded table nor in the table of unbounded prompts fails the test, so a
   new prompt cannot skip the audit.
2. **Result of the audit (tokens at measured rates, with the cap):**

   | Prompt (latest) | Worst case | Cap | Verdict |
   | --- | --- | --- | --- |
   | `risk-analysis` v2 | about 2,500 | 3,000 | fits |
   | `counter-offer` v2 | about 725 | 900 | fits |
   | `risk-citation-repair` v1 | about 800 (quote 300, claim 600, Bangla) | 300 | **did not fit** |
   | `verify-support`, `verify-support-claude` v1 | about 182 (reason 200 characters, text only) | 150 | **did not fit** |
   | `clause-classify` v1 | about 49 per clause, no bound on clauses | 700 | **did not fit** above 12 clauses per call |
   | `query-rewrite` v1 | about 254 (three queries of twelve long Bangla words) | 300 | fits, margin thin |
   | `clause-facts` v1 | no bound on facts per clause | 600 | **cannot be bounded** by the schema |
   | `clause-segment` v1 | no bound on clauses | 600 | **cannot be bounded** |
   | `pii-extract` v1 | no bound on entities | 400 | **cannot be bounded**; rate not measured for Qwen |

3. **Fixes with a new version, nothing frozen is edited.** `risk-citation-repair` v2: at most one
   citation, quote 20 to 120 characters, claim 20 to 100, cap 600 (largest answer about 300 tokens;
   two maximum Bangla citations, about 512, would leave no 15% margin under 600). `verify-support` and `verify-support-claude`
   v2: the reason limit of 200 characters is in the schema, cap 250 (about 182); the wording is
   unchanged. v1 files, schemas and fixtures stay; `LOCK` and `PromptFreezeTest` pin the v2 files.
4. **Fixes in code where the bound comes from the caller.** The classifier sends at most 10 clauses
   per call (`ClauseClassifier.MAX_PER_BATCH`; 567 of 595 tokens) and the fact extractor at most 4
   (`FactExtractor.MAX_PER_BATCH`). A document of up to 20 clauses is still sent in two batches
   (classify) and one of up to 12 in three (facts), so the requests, and the recorded fixtures, do
   not change for the synthetic contracts.
5. **Prompts that cannot be bounded stay as they are, with a stated failure mode.**
   - `clause-facts`: a batch cut at its cap is split in halves and read again; a single clause that
     is still cut gets no facts (its rules cannot be evaluated; the analysis continues). A counter-offer
     re-check never passes on missing facts: that call still throws and the redline is dropped.
   - `clause-segment`: invalid output already falls back to one clause per paragraph.
   - `pii-extract`: capping entities would drop personal data (R-03), so a cut answer fails closed
     with `local-model-unavailable`, as designed.

## Consequences

- A `clause-facts` v2 with a schema bound (facts per clause) is the proper fix; it changes the hash
  of every facts request and, through the outline, of the risk requests, so it waits until that
  recording is redone anyway (about US$0.18). Listed in `docs/known-gaps.md`.
- The verifier prompts are re-recorded for the next live run (18 Haiku calls, under US$0.01); their
  verdicts can differ from the earlier run's.
- Local-model token rates are unmeasured, so the audit says nothing certain about `pii-extract`.

## Alternatives

- Raise only the caps of v1: rejected, a released version is immutable (R-05), and an unbounded
  schema would still allow a larger answer.
- Two citations in the repair answer with a cap of 700: possible; one is enough for the finding to
  become verified, and a smaller cap is cheaper and faster.

## Verification

`PromptAnswerBoundAuditTest`, `RepairAnswerSizeTest`, `ClauseClassifierBatchTest`,
`FactExtractorTest` (batch limit, split, no silent pass), `PromptGoldenTest` (v2 renderings),
`PromptFreezeTest`, `promptLint`.
