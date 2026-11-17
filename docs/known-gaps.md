# Known gaps

A running list of what is not done, not measured or not proven, so nothing is claimed by accident
(R-07, R-12). Each phase report in `docs/dev-log.md` links here. Newest first.

## P10 (evaluation run p10a, 17 Nov 2026)

| Gap | Where it matters | Detail |
| --- | --- | --- |
| Bangla contracts blocked by the egress guard: partly fixed (ADR-0025) | R-03, FR-28, availability | The local PII model labels role words (মালিক, কর্মচারী, স্বাক্ষর) as entities; inflected forms stay in the redacted text and the guard's known-value check blocks the request. Fixed on 17 Nov for whole-value role words with an allowlist (`data/privacy/role-words.yaml`, draft, not yet read by a Bangla reviewer): `syn-bn-01` passes the guard live (48 cloud calls, 0 PII). **Still open:** `syn-bn-03`: the model returns a longer person value containing a role word, the redactor redacts each name part, so `কর্মচারী` and `স্বাক্ষর` are protected values again and the inflected forms block the request (local run `p10c`; guard not re-run). Needs a decision: apply the list to name parts too. |
| `syn-bn-01` ShromikAI run incomplete: stopped by the spend cap at the memo step | M-19, M-20, ADR-0024 | The additional run (17 Nov, step cap US$0.45) spent US$0.3890 on 48 live calls, then `SpendCapException` at COMPOSE refused the memo call: `FAILED(internal-error)`. The recordings of the 48 calls exist, so a rerun replays them and pays only for the remaining calls. Not rerun without the owner's yes. The baseline of `syn-bn-01` was not rerun and still shows `privacy-guard-blocked`. |
| M-25 numbers come from the run before the allowlist | M-25, R-07 | The report's 36 of 36 is from `p10a` (before the fix). The same 36 of 36 was measured after the fix in run `p10b` (`eval/reports/runs/2026-11-17-p10b/`), which the report does not read. |
| A lone name part is not a protected value for the egress guard | R-03 | A surname that occurs only inside longer names is redacted in the text but has no entry of its own in the placeholder map, so the guard would not block it if it re-entered a request. Behaviour from P5, found while testing ADR-0025; not changed. |
| Labels and adversarial claims are unreviewed AI drafts | M-06 to M-11, M-04, M-05 | Written by Claude, same family as the system: circular. 0 reviewed labels. |
| Valid citations accepted 16 of 20 | M-05, FR-44 | Four valid controls ended `SUPPORT_PARTIAL`; the verifier or the drafted claims may be at fault. |
| Correct abstention 1 of 5 | M-11, FR-47 | Risks with no supporting section (transfer, deposit, IP) are mostly shown VERIFIED with a weak citation (see BLA-2006:3). |
| Baseline comparison is mostly key-format failures | M-03, ADR-0024 | 17 of 21 baseline citations point at contract clauses, 4 use malformed keys; baseline prompt fairness never reviewed. |
| `/api/v1/meta` latestEval still null | FR-74 | `/docs/audit` does not show the P10 numbers; `/docs/evaluation` does. |
| CI cannot re-run the verifier on the real law | FR-83 | `eval-smoke` recomputes the gate from committed results; no corpus in CI. |

## P9 (web experience, found while building and reviewing it)

The transfer-risk card in the recorded demo shows `BLA-2006:3` as `VERIFIED`, and that citation does
not clearly support the claim. This card is the documented example of the same-family verifier
limitation: the verifier is a second Claude prompt (`SAME_FAMILY`), so it judges "does this section
support this claim" with the same blind spots as the model that proposed the citation. Our own
cross-check of 17 November found it. We have not changed the recordings, the verifier or the UI to
hide it. Fix planned after 20 November: a cross-family verifier (a Gemini Flash run or another
independent model, [ADR-0018](adr/0018-gemini-verifier-dropped-for-now.md)) plus a
relevance check. Until then, do not call the verifier "independent", and when this card is shown,
say so.

| Gap | Where it matters | Detail |
| --- | --- | --- |
| The transfer finding ("any site, any time") cites `BLA-2006:3` and the quote does not clearly support the claim | FR-47, R-01 | In the recorded sample the transfer finding is `VERIFIED` against `BLA-2006:3` (f9 with the rules off, f11 with them on), but the quoted passage does not clearly support the claim. The support check judges the claim against the section, not whether the section bears on the clause, and every verification is `SAME_FAMILY`; a stricter cross-family judge is future work. (The probation finding now cites `BLA-2006:4`, whose recorded quote is about probation periods; no concern is recorded for it.) |
| The statute portal text contains the Assamese letter ra in some quotes | FR-12, FR-70 | The official Bangla text of the portal uses the Assamese ra (U+09F0, ৰ) in some words where Bangla usually writes the ordinary ra. Quotes and section text are shown verbatim, so these glyphs appear in citations and in the sheet. Nothing is normalised on purpose: a quote must match the official text character for character (R-01). A reader may see two forms of the same letter |
| The obligations list of the recorded demo has two scripted rows | R-12, FR-53 | `obligations` was never recorded. In the `fake-models` profile it is a scripted stand-in with two generic rows (the employer pays the stated salary, the worker works the stated hours). Results from that profile say `recorded` as a whole, and the stand-ins name their model `scripted-demo-standin`; the memo's method section and the compare page say so too. The same holds for the local-PII step, the memo summary and next steps, and the compare baseline |
| The sample contract no longer carries the test canary line | R-02, FR-69 | Fixed on 2026-11-16: the build strips the marker from the sample copy and the demo replays recordings made for the stripped text. The recorded demo contains no marker in its result, redlines, counter-offers or memo (checked by running it). The recording set is a union: calls for clauses whose text did not change replay older recordings (their request hashes still match), the changed clauses use the new ones; the three old counter-offer recordings that carried the marker were removed. Four first answers broke their schema and are kept marked `schemaInvalidAsRecorded`. A real `obligations` answer was recorded but its request does not match the demo's, so the demo still uses the scripted stand-in. Live spend for this: US$0.6679 (US$0.4280 and a second, failed run of US$0.2399); ledger total US$1.6515 of 3.00, which passed the US$1.60 stop line set for this recording (see the dev-log). |
| The 3D hero scene's frame loop is not verified in an automated browser | FR-68 | The scene is created and draws a first frame on a real GPU (Apple M3, checked in the app browser pane); headless Chromium shows the poster. Animation frames do not fire in the app browser pane, so the running animation was not observed |
| Memo PDF depends on the Gotenberg deny-list fix | FR-55, ADR-0020 | The compose deny list blocked the render's own page, so no PDF could be made before P9. It now allows `file:///tmp/`. `MemoPdfIT` is still `slow` and not in CI (P7 review #16), so only the Playwright download test covers it |
| Demo accounts cannot save a profile | FR-56 | `PATCH /auth/me` is not allowed for demo accounts (api.md §2), so the profile page is covered by unit tests only, not by the demo end-to-end |
| Back and Forward transitions rely on `unstable_HistoryRouter` and were not seen as motion | FR-68, NFR-08 | `lib/backForward.ts` makes the router hear about Back and Forward inside `document.startViewTransition`. The router component is named unstable; a unit test and `e2e/c4.spec.ts` count the transitions, but a person has not watched the motion. On a phone the browser's own swipe-back animation is shown instead of ours. |
| The demo database holds about a hundred API keys of earlier test runs | FR-65 | `/admin/api-keys` is very long in the demo (every e2e run creates a key and revokes only one). The page is correct; the data is test residue. |
| Admin users and account deletion, `/docs/pitch`, `/docs/team`, Mermaid diagrams | FR-73, FR-74, FR-78 | Agreed to defer for P9: no admin user CRUD, no `DELETE /auth/me`, no pitch or team pages; the architecture page is a hand-drawn SVG |
| The docs pages show English documents in the Bangla interface | R-11, FR-74, FR-75 | `/docs/mcp`, `/docs/known-gaps`, the prompt pages and the specification sections on `/docs/security` and `/docs/architecture` are the project's own English files, rendered as they are and marked `lang="en"`; only the page frames and the live and report-based pages (`/docs/audit`, `/docs/data`, `/docs/evaluation`) are translated. Diagram sources (`mermaid` fences) inside those sections are replaced by a note. |
| `/docs/evaluation` had no contract numbers (fixed in P10: it reads `eval/reports/report.json`) | R-07, FR-79, FR-74 | Only the retrieval report exists. The contract evaluation (risks found, correct and invented citations) has no `eval/reports/report.json`, so the page says "not yet run" and `/docs/audit` shows no latest evaluation until P10 writes one. |
| Usage report: model cost covers 30 days, KPIs cover 7 days | FR-73, KPI-12 to KPI-14 | The cost per model comes from `analysis_trace`, which is kept for 30 days, so a longer window shows model cost only for its last 30 days; the day totals come from `usage_event` (13 months). The three KPIs always cover the 7 days ending at `to`. `memoDownloadRate` can be above 1 when a memo is rendered more than once. |
| No CI step compares the rule quotes with the corpus | FR-36, R-10 | `checkRules` is opt-in (`-PcorpusCheck`) and CI has no corpus database. The app does check the quotes at startup against its own database; team review and local runs cover the rest for now. |
| Three light-theme 390 px admin screenshots showed the 403 page, and were removed | FR-73, R-12 | `C4-admin-{playbooks,review,trace}-light-390.png` were byte-identical copies of the 403 page (the shot ran as a worker). They are gone from `docs/img/p9/`; the dark 390 and light 1280 admin pictures are unaffected, and the light 390 admin pages have no picture until the screenshot run is repeated. |
| NFR-10 "interactive in 5 s on Slow 3G" is not demonstrated | NFR-10, R-07 | The target is 5 s at 400 kbps and 400 ms RTT. `npm run check:lite` now loads the lite landing under CDP throttling and measured (3 runs, 2026-11-17, local preview server) a load event at 4.7 s and the first link or button visible at 4.8 to 4.9 s, with 510 KB over 12 requests in total. This is a proxy, not time-to-interactive: main-thread blocking is not measured, it is one machine, and the script's pass limit is a 30 s guard. No Playwright `perf-landing` test exists. Do not say "interactive in 5 s on Slow 3G" in the pitch; say "about 5 s load under emulated Slow 3G in our check". |
| The usage charts are a separate 114 KB (gzip) chunk | NFR-10 | Recharts loads only on `/admin/usage` and `/docs/evaluation`, and is replaced by the table in low-bandwidth mode. The charts have no keyboard layer (they are hidden from assistive technology; the table beside them has every number). |
| Playbooks of an admin without an organisation | FR-57 | `POST /playbooks` needs an organisation: an admin without one must send `orgId`, which the page does not offer. The demo admin belongs to the demo organisation, so it works there. |

## P8 acceptance (MCP Inspector CLI, 2026-11-12)

| Gap | Where it matters | Detail |
| --- | --- | --- |
| Local PII model on Docker Ollama for Mac is slow: the first `verify_legal_citation` took over a minute | R-03, FR-65, FR-66, P11 deployment | The claim is redacted by `qwen2.5:7b-instruct` before any cloud call (R-03). In Docker on a Mac without a GPU the model loads in about half a minute and then answers slowly, so the first call after a cold start exceeded the Inspector CLI's 60 s timeout (`docs/img/mcp-cli-session.txt`); a retry with the model warm succeeded. The same cause is in the P6 row on `pii-extract`. **P11 implication:** the deployment host needs the model warm and enough CPU or a GPU (keep it loaded, for example `OLLAMA_KEEP_ALIVE`, and warm it at start-up), and a client timeout above 60 s for `verify_legal_citation` and `analyze_employment_contract` must be documented; the local redaction must never be bypassed to save time. Cold-start and warm latencies are not measured (R-07). |
| Acceptance evidence is the CLI only | FR-67, R-12 | No browser Inspector or Claude Desktop screenshots exist; `analyze_employment_contract` was not run live (cost). See `docs/mcp.md`. |

## P7 and P8 review (Prompt R, PASS WITH FIXES; fixes applied 2026-11-12, these remain open)

Item numbers are those of the review report.

| Gap | Where it matters | Detail |
| --- | --- | --- |
| Keys with scope `laws` do not open the REST law endpoints (#7) | FR-65, NFR-20, ADR-0021 | Scope `laws` authenticates `/mcp` only. From P9 `ANALYSIS`, `COMPARE` and `MEMO_RENDER` events are written for REST requests (organisation level; the user and key are not recorded); `API_CALL` is still written for no request. |
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
| `usage_event` has no `API_CALL` rows and no user or key on REST events | FR-65, FR-73 | `ANALYSIS`, `COMPARE` and `MEMO_RENDER` events are written for REST traffic with the organisation and cost only (an analysis job does not know the user ID, and a guest has none). `API_CALL` is not written. An MCP call writes one `MCP_CALL` event whose cost is the analysis cost (not split from the call). |
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
| Counter-offers are unchecked when no rule applies | FR-41 | Handled by a `counter-offer-unchecked` warning (fixed in the P6 review fixes). The eight Bangladesh rules are active (reviewed by a team member, not a lawyer), so only a redline for a clause that no rule covers carries it. |

## P6 (intelligence core)

| Gap | Where it matters | Detail |
| --- | --- | --- |
| **Cross-family verification was not run live** | FR-44, §7.6, M-04, M-05, any claim of "independent verification" | Gemini is dropped for now (ADR-0018). Every verified citation is `SAME_FAMILY` (Claude Haiku). Do not describe results as verified by an independent or different-family model. |
| Local PII model too slow on the development Mac | R-03, M-16, M-25, §5.3 REDACT budget | `qwen2.5:7b-instruct` in Docker without a GPU loads in about 31 s and runs at about 7 tokens a second, so `pii-extract` times out at 30 s and the job fails closed. The live recordings stand in the contract's ground-truth entity list for this step only. PII recall on the real model is unmeasured. |
| M-19 latency per stage not measured as a distribution | NFR-01, ADR-0016, ADR-0017 | One live run (36 s) is a sample. The budgets are planning numbers. |
| Three findings per risk request | M-06, M-09 | `risk-analysis` v2 reports at most three findings per batch and four batches per contract; the live run did not report the non-compete clause. Recall loss is unmeasured. |
| Only one contract recorded live | M-01 to M-14 | `syn-en-02`, worker side. No side-swap run, no Bangla contract, no `test` split. |
| Two demo modes | FR-36, FR-69 | `scripts/demo-backend.sh` runs with the legal rules on (13 findings, all `VERIFIED`, so no uncertain card and no review item is created) unless `DEMO_RULES=off`, which loads no rule file so the earlier recordings replay (11 findings, 2 `UNCERTAIN`, the "no active rule" notice). The end-to-end tests tagged `@rules-off` (uncertain "why" drawer, review ticket, worker flow, admin review approve) run only against the rules-off backend: `DEMO_RULES=off npm run e2e`. |
| Rules reviewed by a team member, not a lawyer; lawyer review pending | FR-36, R-10 | The eight rules in `data/rules/bd-labour.yaml` are `active` since 2026-11-16, reviewed against the official portal by a team member (not a lawyer); an AI-assisted cross-check was done as an extra signal and is not a legal review. No rule knows the worker category, so the exemptions in `docs/rules-review-checklist.md` are not applied (accepted). A lawyer's review is pending. |
| Changing a rule file changes the recorded request hashes | FR-36, R-07, FR-69 | The active rules feed the recorded requests (rule findings reach the risk agent, and each counter-offer proposal is re-read by `clause-facts`). After any edit of `data/rules/*.yaml` the recorded demo must be re-recorded (record-missing only), or it fails with `not-recorded`. |
| The daily budget counts replayed fixture costs as spend | FR-62 | Replayed recordings are priced at their original cost and added to the day's total, so a long local or end-to-end run reaches the US$50 cap although nothing is spent (an accounting artefact; replays should not count against the cap). The demo script and `LiveContractIT` raise the cap for themselves; no fix yet. |
| One recorded `risk-analysis` answer broke its 120-character quote limit | R-04, FR-47 | In `risk-analysis/v2/syn-en-02-worker/c465f60d1157518c.json` the model returned a quote longer than the schema allows; the gateway repaired it with a second call. The first answer is kept as recorded and marked `schemaInvalidAsRecorded`. |
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

## Cross-check findings, 17 Nov 2026 (AI review, corpus-corroborated; lawyer review pending)

An outside AI review of the redlines and risks was checked against the raw source text of the
Bangladesh Labour Act 2006 (`data/raw/bdlaws.minlaw.gov.bd/act-print-952.html`, the consolidated
text the corpus is built from), **not against the ingested database**. This is not a legal review;
a lawyer's review is pending. All eight claims checked were corroborated by the source text.

1. **Redline 6 understates festival-holiday work.** It says "compensatory leave or wages".
   s.118(3) requires a substitute day **and** two days' compensatory wages when a worker is asked
   to work on a festival holiday (sub-section substituted by Act 58 of 2018, s.17).
2. **Redline 4 omits two compensation rules.** It does not mention the permanent-worker
   compensation of s.26(4) (30 days' wages per full year of service, or gratuity if higher), nor
   the resignation compensation of s.27(4), which the Labour (Amendment) Act 2026 (Act 43 of 2026,
   s.13) substituted: 7 days' wages per full year up to 3 years, 15 days for over 3 and under 10
   years, 30 days (or gratuity if higher) for 10 years or more.
3. **Risk 11 cites a section that does not fit.** It cites s.3, which does not prohibit general
   transfers. s.187 protects only trade-union officers (president, general secretary and other
   officers) against transfer from one district to another without their consent. Already flagged
   as the weakest citation (see "Weak citation relevance can pass the verifier").
4. **Two rules are too coarse.** Rule 4 cannot express 1.5 days of weekly rest or the
   distinction by establishment type (s.103(ka): one day for factories and industry, one and a half
   days for shops and establishments). Rule 7 checks the count of festival holidays only, not work
   on a festival holiday (s.118(3)).

**Fix plan (after 20 Nov):** add a new counter-offer prompt version (`counter-offer` v{n+1}) that
receives the relevant section text (s.118(3), s.26(4), s.27(4)); re-record the demo
(record-missing only, see "Changing a rule file changes the recorded request hashes"); run the
dev-split evaluation; then ask the lawyer to review the changes. Rules 4 and 7 stay coarse until
then, and any rule change needs a reviewer other than the author to sign it off.
