# ADR-0008: Latency and cost budget for the analysis pipeline

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Backend/Data Engineer (TODO: name), Business Analyst/Data Scientist (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0005 (model routing); PRD NFR-01, NFR-03, §9.1, FR-37, FR-41, FR-48, FR-51, FR-53; architecture §5.1, §5.3, §8.5 |

## Context

In `spec-v1`, the cost estimate (PRD §9.1) assumed a single `risk-analysis` call with 12,000
output tokens, a 6,000-token memo and a 5,000-token counter-offer call inside the `RISK` stage, while
the latency budget (architecture §5.3) gave `RISK` 18 s and `COMPOSE` 6 s. Generating 12,000 output
tokens in 18 s needs more than 650 tokens per second from one stream, far above what a reasoning
model can be planned on. The two budgets could not both hold, so NFR-01 (p95 ≤ 60 s) was not
achievable as specified. The provider documentation gives no output-speed figures to plan with.

## Decision

1. **State a planning assumption and measure it.** Budgets assume TTFT ≤ 2 s, Sonnet 5.5 ≥ 80 output tokens per second per stream, and Haiku 5.5 and Gemini Flash ≥ 150. These numbers are assumptions, not measurements. P6 measures TTFT, output speed and duration per stage on the `dev` split.
2. **Parallelise the long call.** `risk-analysis` runs on up to 4 clause batches in parallel (architecture §8.5), each with a contract outline so cross-batch `INTERACTION` findings remain possible; `clause-classify`, `query-rewrite` and `clause-facts` are batched in parallel too.
3. **Cap every output.** Each prompt's `max_output_tokens` is set from the budget (architecture §5.3): 1,400 per risk batch, 800 for the memo narrative, 500 per counter-offer, 1,500 for obligations, 150 per support check. A call that hits the cap is treated as invalid output; its one repair splits the batch in half.
4. **Move work off the critical path.** `obligations` runs in `RISK`, in parallel with the risk batches; its citations are verified in `VERIFY`. `counter-offer` runs after verification, once per `VERIFIED` finding and in parallel, starting during `CORRECT` for findings already verified. `memo-bn` and `memo-en` write only the summary and next steps; the other memo sections are assembled from the structured result.
5. **Split NFR-01 by input kind.** p95 ≤ 60 s for text input and text-layer PDFs and ≤ 70 s for scanned PDFs and photos; the stage budgets sum to 59 s and 67 s including `CORRECT`.
6. **Recalculate cost** with batching, caps, cache-write and cache-read prices and the Haiku 5.5 long-context tier: about US$0.34 per 10-page analysis uncached (PRD §9.1), below the NFR-03 median target of US$0.40.
7. **Change control.** If P6 or P10 measurements miss a stage budget, the response (smaller batches, other caps, a different effort, or a revised NFR-01) is recorded in a new ADR with the measurement; targets are never changed silently.

## Consequences

Positive:

- Latency and cost budgets now add up, and each number names its assumption.
- Counter-offers exist only for verified findings, so no redline rests on an unverified legal claim.
- Lower output volume lowers cost by about US$0.06 per analysis against the v1 estimate.

Negative:

- Batching splits the contract across calls; cross-clause reasoning depends on the outline and must be watched through M-09 (non-obvious recall) on the `dev` split.
- Parallel calls of one analysis cannot share a prompt-cache entry.
- The memo narrative is shorter; plain-language quality (M-21) depends more on the findings' own explanations.
- If the output-speed assumption is wrong, NFR-01 will be missed until a follow-up ADR.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Raise NFR-01 to 120 s for everything | Gives up the phone-user experience without trying the cheaper structural fixes first |
| Use Haiku 5.5 for `risk-analysis` | Faster and cheaper, but legal reasoning quality is the core output; reconsider only with a dev-split ablation |
| Lower `risk-analysis` effort to `low` | Possible later lever; first measure with `medium` (ADR-0005) |
| Stream findings and start counter-offers before verification (spec-v1) | Produces redlines for findings that may end `UNCERTAIN` and keeps a long call on the critical path |

## Verification

- `OrchestratorStateMachineTest`: batches run in parallel; counter-offers are requested only for `VERIFIED` findings; a capped batch is split once.
- `CounterOfferRuleCheckIT`: an `UNCERTAIN` finding gets no counter-offer call.
- M-19 per stage and per input kind on the `dev` split in P6 and on the `test` split in P10; M-20 for cost; M-09 to watch cross-batch reasoning.
