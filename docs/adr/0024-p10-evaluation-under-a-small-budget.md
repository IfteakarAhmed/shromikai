# ADR-0024: P10 evaluation scope under a US$1.45 live budget

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-17 |
| Deciders | Project owner (decided the budget, the option and the cuts in the P10 session), Claude (author) |
| Related | ADR-0015 (live spend cap, amended), ADR-0018 (Gemini dropped), ADR-0022; rules R-01, R-02, R-07, R-08; evaluation.md §5, §8, §9, §13; PRD FR-79 to FR-83 |

## Context

evaluation.md plans 12 to 15 contracts, four systems, three runs each, a reviewed label set, a
legal reviewer's verdict files and a calibrated clarity judge. On 17 November the API balance was
about US$3.12 and the owner kept US$1.60 for the demo. The ledger had spent US$1.84 of its US$3.00
cap. Only the five P5 synthetic contracts exist (the dev split); there is no test split, no
Bangla–English pair, no OCR or injection set, no human rating and no reviewer. One full
ShromikAI run costs about US$0.43 (ledger, 16 November); the baseline was never run live.

## Decision

1. **Caps.** The ledger cap becomes US$3.30, and every evaluation call (ledger purpose `eval:`) has
   its own cumulative cap of US$1.45 (ADR-0015 amendment). The eval wrapping also refuses a call
   that could pass the current step's cap; calls in flight count against the caps.
2. **Option A of the P10 plan.** ShromikAI full pipeline on three contracts, worker side, one run:
   `syn-en-02` replays the demo recordings of 16 November, then `syn-bn-01` and `syn-en-01` live;
   a fourth contract only if eval spend after two is at most US$0.85. The baseline runs on all five
   contracts; the adversarial citation set runs once through the verifier.
3. **Recordings.** New recordings go to `backend/src/test/resources/fixtures/llm-eval/`, record-
   missing only. Demo recordings are read, never written. No rule file and no prompt is touched.
4. **Local PII model.** The evaluation runs the real `qwen2.5:7b-instruct` with a 15-minute per-call
   timeout in the eval wrapping only; production stays fail-closed at the prompt's 30 s. A contract
   whose local step takes longer uses its ground-truth entity list, and M-16 and M-25 report the
   model step as not measured for it.
5. **Ablations without extra spend.** `no-verifier` is derived from the raw risk-agent claims of the
   same runs and is labelled so; `no-rules` and `verifier-same-family` are reported as not run.
6. **Cuts, reported, never estimated.** LLM-as-judge (M-21, M-26) is not done. M-02, M-27 and M-28
   need a reviewer's verdicts and are not available. M-13 (no pairs), M-14 (employer side), M-18
   (no injection set), M-22 (facts not kept) and M-24 (no OCR set) are not available or not run.
7. **Labels.** Claude drafts the five label files and the adversarial claims (`status: draft`,
   `reviewed_by: null`). They share the system's model family, so the report calls them circular
   and states "0 reviewed labels". The CSV export is for the law student who reviews them later.
8. **CI gate on recorded results.** `eval-smoke` recomputes M-01, M-04 misses, M-16 and M-17 from
   the committed results of the last run and fails when a gate is broken or when `report.json`
   states other numbers; `EvalRunnerSmokeIT` runs the runner on scripted stand-ins. CI has no
   statute corpus, so it cannot re-run the verifier on the real law.

## Consequences

- Every number in the report rests on three to five synthetic contracts and is labelled indicative;
  no difference interval or significance claim is made.
- M-04's independent evidence is the adversarial set; baseline and raw-agent citations that fail the
  existence or quote check are caught by the same check that defines them, which the report says.
- The P10 numbers can be re-run in full once a reviewer, a test split and more budget exist.

## Alternatives

- Six to eight contracts, as first suggested: does not fit US$1.45 at about US$0.45 to 0.60 per
  Bangla contract-side.
- Option B (counter-offers and memo narrative off, four contracts): rejected by the owner to keep the
  full pipeline under test.
- Estimating the cut metrics from the dev runs: forbidden by R-07.

## Verification

`MetricFormulaTest`, `LabelSchemaTest`, `ReportGeneratorTest`, `JudgeCalibrationTest`,
`EvalGateTest` (seeded regressions), `AdversarialGeneratorTest`, `EvalRunnerSmokeIT`, `MetricsIT`;
`LiveSpendLedgerTest` for the caps; the ledger file after the run; `./gradlew evalSmoke`.
