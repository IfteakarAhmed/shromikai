# ADR-0016: Bounded risk answer, a 3,000-token cap and a revised RISK budget

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0008 (decision 7 asks for an ADR when a measurement misses a budget), ADR-0015 decision 10 (thinking off); PRD NFR-01, FR-37; architecture §5.3, §8.5, §18.6 C38; evaluation M-19 |

## Context

ADR-0008 set the `risk-analysis` output cap at 1,400 tokens per batch so that `RISK` fits 20 s at an
assumed 80 output tokens per second, and said a miss would be recorded in a new ADR with the
measurement. The first live run on a synthetic contract (the shortest, worker side, Claude Sonnet
5.5, thinking off) measured this:

- every `risk-analysis` call produced exactly 1,400 tokens and stopped at the cap, so the answer
  was truncated JSON and invalid; six calls (four batches and the half-batch retries) returned no
  result and cost US$0.286;
- input per call was 6,900 to 23,500 tokens (candidate statute sections in Bangla dominate it);
- the `RISK` step took 27 s for one round of four parallel calls followed by two sequential
  half-batch retries, which is about 9 s per call of 1,400 tokens including prefill, so about 150
  output tokens per second per call. One run is a sample, not a p95.

So v1's cap was too small for what the prompt asked for (up to four findings or more per batch,
each with a quote of up to 300 characters, a claim of up to 600 and a long explanation). Raising only
the cap is not enough: an unbounded answer can exceed any cap, and a truncated answer is a failed
call that is paid for.

## Decision

1. **A new prompt version, not an edit.** `risk-analysis` v2 and its schema `risk-analysis.v2.schema.json`; v1 and its schema stay as they were (frozen, in `prompts/LOCK`).
2. **Bound the answer in the schema.** At most 3 findings per request (the most severe, the less obvious first), at most 2 citations per finding, `title` at most 50 characters, `explanation` at most 160 characters (two short sentences; it carries what `why_it_matters` said, and that field is gone), `quote` at most 120, `claim` at most 100. `maxItems` and `maxLength` validate locally (R-04); the Anthropic adapter removes size keywords from the copy it sends, because the API accepts only a subset of JSON Schema, so an answer that breaks a limit gets one repair.
3. **Raise the cap to 3,000 output tokens and prove the bound.** `RiskAnswerSizeTest` builds the largest answer the schema allows (3 findings, every field at its limit, written in Bangla, which is the default output language) and estimates its tokens with rates measured on 15 recorded real answers (JSON text 0.65 tokens per character, Bangla 0.9, rounded up to 0.70 and 0.90): about 2,510 tokens, 84% of the cap. At a harsher 0.80 and 1.00 it is about 2,820, still under the cap. The test fails if a limit or the cap changes so that this no longer holds.
4. **Revise the `RISK` budget and NFR-01.** On the planning assumption of 80 tokens per second, `RISK` is 2 s + 3,000 ÷ 80 = 39.5 s, budgeted at **40 s** (was 20 s). The stage budgets then sum to **79 s** for text and text-layer input and **87 s** for scans and photos, so NFR-01's p95 targets become **80 s and 90 s** (were 60 s and 70 s). The single live sample suggests about 150 tokens per second, which would put `RISK` near 20 s and the total near the old targets; that is why the budget stays a planning number until M-19 is measured (P10 on the `test` split). If the measured p95 is better, the targets are tightened by a new ADR, never silently.
5. **Raise the timeouts to match.** The `RISK` step timeout goes from 80 s to **110 s**, and the whole-analysis hard timeout from 120 s to **150 s** (`analysis-timeout`). They are cancel limits, above the budgets. The prompt's own timeout is 90 s.
6. **Keep the limit of 4 batches.** At most 12 findings per contract result from 4 batches of 3. That is a loss of recall for a contract with more risks than that; M-06 and M-09 on the `dev` split measure it.

## Consequences

Positive:

- A risk call cannot exceed its cap (by the estimate above), so the expensive failure of the first live run cannot repeat from size alone.
- The answer is shorter, so cost per call is lower than an unbounded one.

Negative:

- The p95 latency target is looser by 20 s until measured, and the synchronous window of the public API (60 s, FR-58) will rarely be met by a full analysis; P7 decides whether it stays.
- A batch with more than three real risks reports three. Explanations of 160 characters are short, in Bangla about two lines.
- v1 stays in the repository unused, as a record.

## Alternatives

- Raise v1's cap to about 3,000 with no bound: rejected, a denser contract can still exceed it, and the cost of a truncated call is the full input.
- More batches of one clause: more calls, and each repeats the large statute context, so it costs more.
- Drop the quote and claim from the answer: the verifier needs them (FR-42 to FR-44).

## Verification

`RiskAnswerSizeTest` (maximum answer, limits in the schema and the text in step), `PromptGoldenTest`
(v2 rendering), `PromptFreezeTest`, `OrchestratorStateMachineTest`; the first live batch of v2 reports
the stop reason, the finding count and the tokens (phase report).
