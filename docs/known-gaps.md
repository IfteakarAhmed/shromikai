# Known gaps

A running list of what is not done, not measured or not proven, so nothing is claimed by accident
(R-07, R-12). Each phase report in `docs/dev-log.md` links here. Newest first.

## P7 and later (found in the P6 review, not yet fixed)

| Gap | Where it matters | Detail |
| --- | --- | --- |
| **Review ticket (FR-50) is not delivered** | FR-50, E-52..E-54 | Review items are opened and stored (content-free), but there is no review ticket for the user: no `ReviewTicketStatus` endpoint, no claim or decision flow, no per-finding ticket link in the result. P7 builds it. Until then an `UNCERTAIN` finding says it is routed to review, and nothing a person can act on exists. |
| `expected_topics_missing` is always empty | FR-37, `risk-analysis` input, ADR-0015 | The pipeline passes an empty list. Filling it needs a list of topics a contract of the worker's category is expected to cover, which does not exist: `data/mappings/bd-topics.yaml` maps clause type to sections, not category to expected topics, `TopicMap` is internal to `retrieval`, and no worker category is applied (no category terms in the corpus). Omission findings therefore come only from the model's own reading of the clause outline. Needs a topic-expectation data file with author and reviewer, a public read API on `retrieval`, and an eval of the effect. |
| Finding merge matching and dedupe key | FR-49 | `FindingMerge` matches a rule finding with a model finding by section key and clause overlap and dedupes on a key that can merge two distinct risks on one section, or keep a duplicate when the model cites a different section. Not yet measured. Review item #7. |
| Repair query reason codes; repair runs for `VERIFIER_UNAVAILABLE` | FR-47, cost | The repair query is built from the finding's title and explanation without the failed reason code, and repair is attempted when the verifier itself was unavailable, which spends a call that cannot succeed. It should skip repair for `VERIFIER_UNAVAILABLE`. Review item #8. |
| Budget race | FR-62, NFR-06 | `ModelSession` checks the budget before a call and charges after it, so parallel batches can overshoot the per-job cap by the cost of the calls in flight. Review item #9. |
| Clock, dead code and executor clean-up | maintenance | Mixed `Clock` and `System.currentTimeMillis()` use in the pipeline, some unused members, and per-call virtual-thread executors in `CounterOfferAgent` and the pipeline. Review item #12. |
| Counter-offers are unchecked when no rule applies | FR-41 | Handled by a `counter-offer-unchecked` warning (fixed in the P6 review fixes). The Bangladesh rules are all draft, so every Bangladesh redline carries it today. |

## P6 (intelligence core)

| Gap | Where it matters | Detail |
| --- | --- | --- |
| **Cross-family verification was not run live** | FR-44, §7.6, M-04, M-05, any claim of "independent verification" | Gemini is dropped for now (ADR-0018). Every verified citation is `SAME_FAMILY` (Claude Haiku). Do not describe results as verified by an independent or different-family model. |
| Local PII model too slow on the development Mac | R-03, M-16, M-25, §5.3 REDACT budget | `qwen2.5:7b-instruct` in Docker without a GPU loads in about 31 s and runs at about 7 tokens a second, so `pii-extract` times out at 30 s and the job fails closed. The live recordings stand in the contract's ground-truth entity list for this step only. PII recall on the real model is unmeasured. |
| M-19 latency per stage not measured as a distribution | NFR-01, ADR-0016, ADR-0017 | One live run (36 s) is a sample. The budgets are planning numbers. |
| FR-58 synchronous window is 45 s, not 60 s | FR-58, NFR-01, ADR-0020 item 14 | Decided in P7: the endpoint waits at most 45 s for JSON text of up to 8,000 characters and otherwise answers `202`. The PRD text still says 60 s. The stage budgets add up to 84 s, so a typical analysis is answered `202`. |
| Three findings per risk request | M-06, M-09 | `risk-analysis` v2 reports at most three findings per batch and four batches per contract; the live run did not report the non-compete clause. Recall loss is unmeasured. |
| Only one contract recorded live | M-01 to M-14 | `syn-en-02`, worker side. No side-swap run, no Bangla contract, no `test` split. |
| Rules are drafts | FR-36, R-10 | All eight rules in `data/rules/bd-labour.yaml` are `draft`; none is evaluated until a team member reviews it (`docs/rules-review-checklist.md`). No worker-category terms exist in the corpus, so exemptions are not applied. |
| ILO mapping unreviewed and unused | FR-16, FR-39 | No ILO reference is produced; `iloReferences` is empty. |
| Statute sections are not prompt-cached | cost | Only the system prompt is marked cacheable; the sections sit in the user turn. |
| Prompts are `experimental` | AGENTS.md §7 item 6 | No dev-split evaluation report is attached to any P6 prompt yet (P10). |
| Stand-in tests prove behaviour, not model quality | R-07 | `GoldenAnalysisIT`, `SideSymmetryIT` and `AllResponsesCarryOnlyVerifiedCitationsIT` use scripted stand-ins on a fictional act. |
| Three prompts cannot be bounded | NFR-01, R-03 | `clause-facts` (facts per clause), `clause-segment` (clauses per document) and `pii-extract` (entities per chunk) have no output bound in their schemas (ADR-0019, `PromptAnswerBoundAuditTest`). `clause-facts` splits a batch cut at its cap and gives a clause that is still cut no facts; `clause-segment` falls back to one clause per paragraph; `pii-extract` fails closed. A `clause-facts` v2 with a bound would invalidate the live recording of the risk step (about US$0.18), so it waits for the next re-recording. Token rates are not measured for the local Qwen tokenizer. |
| Weak citation relevance can pass the verifier | FR-47 | The support check judges claim against section, not whether the section bears on the clause: the c2 transfer finding was verified against `BLA-2006:3` (first full live run). The 0.55 seen on that finding is the risk agent's own confidence in the finding, not the verifier's support confidence. |
| Playbook `statuteSectionKey` is not verified | FR-57, architecture §9 | The key is stored and returned but not turned into a verified citation; a playbook finding never carries a citation. |
| `clauses[].facts` is empty in the result | FR-33, api.md §5.3 | The job does not keep the typed facts after the pipeline; the result's clauses show `facts: []`. |
| Compare is not implemented | FR-59, E-18 to E-20 | `POST /api/v1/compare` and its result and events are missing; `baseline` v1 exists and is audited, and `GuestQuotaService.reserve` takes a weight for it. `CompareApiIT` does not exist. |
| Account profile endpoint is missing | FR-56, E-09 | `PATCH /api/v1/auth/me` is not implemented; guests and accounts send the profile per request. `OpenApiContractTest` lists it. |
| SSE sends only `COMPLETED` stage events | FR-58 | No `STARTED` or `SKIPPED` states; the stream polls the job every 100 ms. |
| Memo clarity is not measured | FR-51, M-21 | `PlainLanguageLintTest` checks average sentence length only; the clarity judge is P10. |
| `usage_event` and `MEMO_RENDER` are not written | FR-55, NFR-20 | `POST /memos/render` writes no usage event yet (the table is not in V7). |
| Per-user concurrency limit is a P7 choice | NFR-20 | Signed-in users without an organisation may run 2 analyses at once; the PRD names only guests and organisations. |
