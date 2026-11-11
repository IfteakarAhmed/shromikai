# ADR-0018: Gemini verifier dropped for now; the same-family Claude verifier stays

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0002, ADR-0005, ADR-0015; PRD FR-44; architecture §6, §7.6, §18.6 C43; evaluation M-04, M-05 |

## Context

ADR-0005 routes the citation support check (check c) to Gemini Flash so that the checker is a
different model family from the risk agent, and falls back to `verify-support-claude` on Claude
Haiku 5.5 (flagged `SAME_FAMILY`). In the live runs of P6 the Gemini path never produced a verdict:

- `gemini-3.8-flash` and other 3.x names: an empty HTTP 404 on `generateContent` for the team's key (earlier, HTTP 403 for a project without billing);
- `gemini-2.5-flash`: closed to new users;
- the newer Interactions API would need a new adapter and a new response schema.

Only the Claude fallback has been run against a real model: a smoke call and one full analysis.

## Decision

1. **The verifier is the Claude fallback for now.** `GEMINI_MODEL` stays empty, so `SupportVerifier` goes straight to `verify-support-claude` (Haiku 5.5, thinking off, effort low). Every verified citation carries `verifierIndependence = SAME_FAMILY` and the result carries warning `verifier-same-family`.
2. **No code change.** The Gemini adapter, `verify-support` v1 and the fallback logic stay as they are, tested against WireMock stand-ins and recorded response shapes written by hand (not recorded from a live call). They are not deleted, so a working key or a new adapter can switch them on by configuration.
3. **Honest wording.** No document, UI text, memo, report, metric or submission field may say that citations were verified by an independent or different-family model while only the fallback runs. README, the submission map, the tooling disclosure and the traceability table say so; the phase report lists it as a known gap.
4. **What the same-family check still gives.** Existence, currency and candidate-set membership (check a) and the quote match (check b) are deterministic and do not depend on the model; they are the checks that stop a fabricated or misquoted section (R-01). Check (c), whether a real section supports the claim, is judged by a smaller Claude model with different instructions from the risk agent; its errors can correlate with the risk agent's more than a Gemini verdict would, which is the reason for the original design.

## Consequences

- M-04 (fabricated-citation catch rate) and M-05 (valid-citation acceptance) are measured with a same-family verifier; the evaluation report must state it.
- The "independent model" statements of the PRD, the playbook and the submission text describe the design, not what has been run.
- Cost: the verifier is about US$0.003 per analysis (18 calls of Haiku in the live run).

## Alternatives

- Another cloud family (OpenAI or others): a new adapter, a new key, a new row in the data-flow tables and the egress guard; not decided here.
- A local model as verifier: weaker, and the local model is already too slow on the development machine (architecture §18.6 C39).
- Build the Gemini Interactions API adapter now: unverifiable without a working key; deferred.

## Verification

`SupportCheckContractTest` (fallback path, `SAME_FAMILY`), `CitationVerifierTest`, the live
smoke call of `verify-support-claude`, and the full live analysis (18 verdicts, all `SAME_FAMILY`).
Cross-family verification has **not** been run live.
