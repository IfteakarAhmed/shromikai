# Known gaps

A running list of what is not done, not measured or not proven, so nothing is claimed by accident
(R-07, R-12). Each phase report in `docs/dev-log.md` links here. Newest first.

## P7 and P8 review (Prompt R, PASS WITH FIXES; fixes applied 2026-11-12, these remain open)

Item numbers are those of the review report.

| Gap | Where it matters | Detail |
| --- | --- | --- |
| Keys with scope `laws` do not open the REST law endpoints; REST calls write no usage events (#7) | FR-65, NFR-20, ADR-0021 | Scope `laws` authenticates `/mcp` only. `API_CALL`, `ANALYSIS`, `COMPARE` and `MEMO_RENDER` usage events are written for no REST request (user, guest or key), so the admin usage views would be empty for REST traffic. Includes the old "`MEMO_RENDER` is not written" row. |
| Review ticket answers 410 after retention (#10) | FR-50 | After the retention job expires a review item, its ticket endpoint answers `410`. Recorded as reported by the review; the exact behaviour for a ticket decided before expiry is to be confirmed from the review report. |
| Result envelope is not bound to the subject (#15) | FR-52, R-12 | The signed envelope is not tied to the subject it was issued to. Recorded as reported by the review; the consequence is to be confirmed from the review report. |
| `MemoPdfIT` is not in CI (#16) | FR-55 | The test is tagged `slow` (it needs Gotenberg) and CI does not run `slowTest`, so PDF shaping is verified only when run by hand. |
| Weak assertions in `ObligationsAgentTest` and `CompareApiIT` (#17) | R-08 | The review found assertions too weak to catch a wrong result. Strengthen them in a later change. |
| MCP `Origin` header check and prompt template outside R-05 (#20) | FR-64, R-05, T-12 | `/mcp` does not check the `Origin` header, and the `review-employment-contract` prompt template lives in `mcp/prompts/`, outside the `prompts/{id}/v{n}.md` standard, so `promptLint`, `LOCK` and the R-05 mirror rule do not cover it. |
| The RISK trace event names one prompt of two | NFR-15, FR-58 | `risk-analysis` and `obligations` run in parallel inside the RISK stage and the stage writes one trace event, so its `promptId`, version and model are those of whichever call was charged last. Per-prompt provenance is correct in `promptVersions` and `models` of the result. `OrchestratorStateMachineTest` asserts accordingly. A separate event per prompt would fix it. |
| Daily cap of `analyze_employment_contract` is per instance and in memory | FR-66, NFR-06 | `shromikai.mcp.analyze-per-key-per-day` (default 10) is counted in a Bucket4j bucket per key, like the per-minute limit, so it resets on restart and is not shared between instances. |
| Compare baseline claims are the one R-01 exemption | R-01, ADR-0022 | AGENTS.md R-01 does not yet name the exemption; ADR-0022 records it. A `docs(spec)` edit of R-01 is pending the team's decision. |

## P8 (MCP server)

| Gap | Where it matters | Detail |
| --- | --- | --- |
| API keys open only `/mcp` | FR-65, ADR-0021 | A key with scope `analyses`, `laws` or `automation` authenticates nowhere yet; REST endpoints still need a user, guest or demo token. The n8n key (`automation`) follows in P11. |
| Rate limit counts MCP calls only | FR-65, api.md §8 | The 60 per minute per key apply to tool calls and resource reads. "Across REST and MCP" is true only once keys are accepted on REST. The buckets are per instance and in memory. |
| `usage_event` covers MCP calls only | FR-65, NFR-20 | `ANALYSIS`, `COMPARE`, `MEMO_RENDER` and `API_CALL` events are still not written for user and guest requests; the admin usage views are P9. An MCP call writes one `MCP_CALL` event whose cost is the analysis cost (not split from the call). |
| Quote reaches the support model after the 92% match | R-03, architecture §10.7 | The quote of `verify_legal_citation` is compared with the official text before the support check, but a quote that matches at 92% could still carry up to 8% foreign characters; the egress guard scans the payload for known identifiers and PII patterns, it cannot know an unknown name inside the quote. |
| Claude Desktop and Inspector not yet run by a person | FR-67 | `docs/mcp.md` describes both; no screenshot exists in `docs/img/` until the team takes them. The Claude Desktop `mcp-remote` configuration follows that tool's documentation and was not run. |
| MCP servers used during development are not recorded | FR-67, R-12 | The honest list in `docs/mcp.md` says "none recorded" for the P8 session; the team must add what it actually used. |
| `search_bd_law` ignores `language` | FR-64 | The argument is validated and then not used for filtering: bge-m3 shares one space across Bangla and English (same as `GET /laws/search`). |
| MCP analyses are not stored | FR-66 | The job is deleted when the result is built, so a client that loses the response cannot fetch it again; only the content-free run row and trace stay (30 days). The review ticket has no token, so it cannot be claimed through the REST ticket flow. |
| `json-schema-validator` moved to 2.0.0 | build | The MCP SDK 0.18.3 needs the 2.x line (a 1.5.9 pin made the server fail at start with `NoClassDefFoundError`). Only `JsonSchemas` uses it; all schema tests pass, but message texts of validation errors are the library's. |

## P7 and later (found in the P6 review, not yet fixed)

| Gap | Where it matters | Detail |
| --- | --- | --- |
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
| SSE sends only `COMPLETED` stage events | FR-58 | No `STARTED` or `SKIPPED` states; the stream polls the job every 100 ms. |
| Memo clarity is not measured | FR-51, M-21 | `PlainLanguageLintTest` checks average sentence length only; the clarity judge is P10. |
| Per-user concurrency limit is a P7 choice | NFR-20 | Signed-in users without an organisation may run 2 analyses at once; the PRD names only guests and organisations. |
| Compare baseline runs after the analysis | FR-59 | The baseline starts when the analysis has finished, so `GET /compare/{id}` stays `202` for one more model call. Compare events are the analysis events (no baseline stage); the compare ID is the analysis ID. `baseline` v1 has no dev-split report. |
| Stored profile covers wording only and is read per request | FR-56 | `PATCH /auth/me` validates and stores the profile; an analysis uses it only when the request sends none. `side` of the profile is not applied to the analysis. |
