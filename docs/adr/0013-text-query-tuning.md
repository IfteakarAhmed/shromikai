# ADR-0013: Full-text query tuning on a dev question set

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | FR-19, FR-23; evaluation.md §2, §11; architecture §8.2; ADR-0012; P4 follow-up |

## Context

The first P4 run (`eval/reports/retrieval.md` at git `ed55169`/`79baa9f`) over the 40 test-split
questions showed hybrid + graph below vector only on hit@5 (36/40 against 40/40). The text list was
weak (text only 6/40): in the OR query, function words that occur in most sections (`কোন` in 313 of
563) outrank the discriminating words, and reciprocal-rank fusion gives that list the same weight as
the vector list. Tuning on those 40 questions would turn the next table into a training result
(evaluation.md §11), so a separate dev set was written first.

## Decision

1. **Dev set.** `eval/retrieval/dev-questions.jsonl`: 37 questions (16 Bangla, 21 English), `draft`,
   written from sections that no test question expects (the two key sets are disjoint, checked
   when the file was written). Only `RetrievalDevTuningIT` reads it; it never reads the test set.
2. **Variants and rule, fixed before the run.** Eight text-query variants (untuned; word document
   frequency cut-off 0.20, 0.10, 0.05; language filter; each cut-off with the language filter).
   Selection: highest hybrid + graph hit@5, then MRR@10, then fewer knobs.
3. **Result on dev** (`eval/reports/retrieval-dev.md`): a cut-off of 0.10 wins. The language
   filter changed no dev metric, so it ships **off**; the option stays (tested) for corpora where
   English and Bangla acts compete.
4. **Mechanism.** `TextQueryBuilder` extracts the lexemes with PostgreSQL itself, drops those whose
   document frequency exceeds `max(20, ceil(0.10 × current sections))`, keeps at least the 3 rarest,
   and builds the OR query. Document frequencies come from `ts_stat` over current sections and are
   cached for 10 minutes. `TextTuning` on `SearchOptions` lets the evaluation run the untuned
   behaviour through the same code path.
5. **Test set once.** After selection the test set is run once; the report also shows the untuned
   rows measured in the same run, so the effect is visible without trusting an older file.

## Consequences

- The full-text list is cheap and no longer dominated by function words.
- Document frequencies are a statistic of the current corpus; they shift when the corpus changes
  (cached 10 minutes; the 20-section floor keeps tiny test corpora unaffected).
- The dev questions are drafts like the test questions; neither set is reportable as reviewed until
  a team member sets `checked`.
- The weights of RRF are unchanged (FR-19 stays as written).

## Alternatives considered

- Weighted RRF: changes FR-19; not needed on the dev evidence.
- Stop-word list: Bangla and English lists would be hand-made and miss corpus-specific words; the
  frequency cut-off needs no list.
- A smaller `text-top-k`: does not fix the ranking inside the list.

## Verification

`TextQueryBuilderTest`, `HybridSearchIT` (language filter), `RetrievalDevTuningIT`,
`RetrievalEvalRunnerIT`; `eval/reports/retrieval-dev.md`, `eval/reports/retrieval.md`.
