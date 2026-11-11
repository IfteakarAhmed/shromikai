# Known gaps

A running list of what is not done, not measured or not proven, so nothing is claimed by accident
(R-07, R-12). Each phase report in `docs/dev-log.md` links here. Newest first.

## P6 (intelligence core)

| Gap | Where it matters | Detail |
| --- | --- | --- |
| **Cross-family verification was not run live** | FR-44, §7.6, M-04, M-05, any claim of "independent verification" | Gemini is dropped for now (ADR-0018). Every verified citation is `SAME_FAMILY` (Claude Haiku). Do not describe results as verified by an independent or different-family model. |
| Local PII model too slow on the development Mac | R-03, M-16, M-25, §5.3 REDACT budget | `qwen2.5:7b-instruct` in Docker without a GPU loads in about 31 s and runs at about 7 tokens a second, so `pii-extract` times out at 30 s and the job fails closed. The live recordings stand in the contract's ground-truth entity list for this step only. PII recall on the real model is unmeasured. |
| M-19 latency per stage not measured as a distribution | NFR-01, ADR-0016, ADR-0017 | One live run (36 s) is a sample. The budgets are planning numbers. |
| Three findings per risk request | M-06, M-09 | `risk-analysis` v2 reports at most three findings per batch and four batches per contract; the live run did not report the non-compete clause. Recall loss is unmeasured. |
| Only one contract recorded live | M-01 to M-14 | `syn-en-02`, worker side. No side-swap run, no Bangla contract, no `test` split. |
| Rules are drafts | FR-36, R-10 | All eight rules in `data/rules/bd-labour.yaml` are `draft`; none is evaluated until a team member reviews it (`docs/rules-review-checklist.md`). No worker-category terms exist in the corpus, so exemptions are not applied. |
| ILO mapping unreviewed and unused | FR-16, FR-39 | No ILO reference is produced; `iloReferences` is empty. |
| Statute sections are not prompt-cached | cost | Only the system prompt is marked cacheable; the sections sit in the user turn. |
| Prompts are `experimental` | AGENTS.md §7 item 6 | No dev-split evaluation report is attached to any P6 prompt yet (P10). |
| Stand-in tests prove behaviour, not model quality | R-07 | `GoldenAnalysisIT`, `SideSymmetryIT` and `AllResponsesCarryOnlyVerifiedCitationsIT` use scripted stand-ins on a fictional act. |
| Weak citation relevance can pass the verifier | FR-47 | The support check judges claim against section, not whether the section bears on the clause: the c2 transfer finding was verified against `BLA-2006:3` at confidence 0.55 (first full live run). |
