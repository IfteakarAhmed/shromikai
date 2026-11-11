# ADR-0017: Bounded counter-offer answer and a smaller repair context

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0008 (decision 7), ADR-0015, ADR-0016; PRD NFR-01, NFR-03, FR-41, FR-46; architecture §5.3, §7.5, §18.6 C41, C42 |

## Context

The first complete live analysis of the shortest synthetic contract (worker side, Claude Sonnet 5.5
for the agents, Haiku 5.5 as the same-family verifier) ran to the end in 36 s for about US$0.35, and
exposed two problems (measurements, one run):

1. **Counter-offers were cut at their cap.** Nine `counter-offer` v1 calls: six returned 317 to 455
   tokens, three returned exactly 500, the cap, and their redlines were dropped
   (`counter-offer-dropped` three times, about US$0.04 spent for nothing). The answers are in
   Bangla by default and v1 allowed a rationale of up to 600 characters and an unbounded clause.
2. **Repair calls carried all 40 candidates.** Two `risk-citation-repair` calls cost US$0.198, 41%
   of the run, because each sent every candidate section of the analysis (about 48,000 input tokens)
   although a repair needs the sections found for one finding.

## Decision

1. **`counter-offer` v2 with its schema; v1 stays untouched.** `proposed_text` at most 600 characters (the full revised clause), `rationale` one plain sentence of at most 140; `max_output_tokens` 900, timeout 45 s. The limits are in the schema (validated locally; the Anthropic adapter sends a size-free copy, ADR-0016). `CounterOfferAnswerSizeTest` builds the largest allowed answer in Bangla and estimates it with the measured token rates (JSON text 0.65 per character, Bangla 0.9, rounded up to 0.70 and 0.90): about 725 tokens, 81% of the cap; at a harsher 0.80 and 1.00, about 807, under the cap. A clause longer than 600 characters cannot be proposed in full: the agent is told to change only what the risk needs, and the finding keeps its verified citation without a redline if it cannot (`counter-offer-dropped`).
2. **Revise the `COMPOSE` budget and NFR-01.** On the planning assumption (80 output tokens per second) a counter-offer is 2 s + 900 ÷ 80 = 13.3 s plus a re-check of up to 3 s, so `COMPOSE` is budgeted at **17 s** (was 12 s), the stage budgets sum to **84 s** (text) and **92 s** (scans), and NFR-01's p95 targets become **85 s and 95 s** (ADR-0016 had 80 s and 90 s). The live run's `COMPOSE` took 7.5 s and the whole analysis 36 s; one run is not a p95, so these stay planning numbers until M-19 is measured (P10).
3. **A repair call sees at most 12 candidate sections.** In order: the sections of the corrective search (the reason for correcting), the sections retrieved for the finding's own clauses, then the first of the analysis-level set (topic-map first, then by score) to fill up. The citation enum of the repair call is restricted to those 12, and attempt 2 accepts a citation only if it is one of them. The limit is the constant `CorrectiveLoop.MAX_REPAIR_CANDIDATES`. Expected effect, from section sizes: repair input of about 15,000 tokens instead of about 48,000, so about US$0.03 instead of US$0.1 per call; this is an estimate, to be measured in the next live run.
4. **The risk prompt and the verifier are unchanged.** The analysis-level candidate set stays 40 (architecture §8.3); only the repair call narrows it.

## Consequences

Positive:

- A counter-offer call cannot exceed its cap (by the estimate), so the dropped-redline failure of the live run cannot repeat from size alone.
- `CORRECT` costs about a third as much, and a repair cannot cite a section it was not shown.

Negative:

- A repair for an `OMISSION` or a vague finding may miss a section that sat in the 40 but outside the 12; it then abstains, which is the honest outcome, and the finding goes to review. M-04 and the review-queue volume on the `dev` split show whether this costs too much.
- Redlines for long clauses are dropped; rationales are one sentence.
- The latency targets are looser again (5 s more than ADR-0016).

## Alternatives

- Raise v1's counter-offer cap only: rejected, same reasoning as ADR-0016.
- Send the repair call only the section the failed citation named: it would repeat the same mistake; the corrective search exists to bring new sections.
- Retrieve fewer than 40 for the analysis: touches retrieval quality (M-15) and the risk step; not decided here.

## Verification

`CounterOfferAnswerSizeTest`, `CorrectiveLoopCandidatesTest` (at most 12 shown, order, enum, attempt 2
restricted), `PromptGoldenTest` (v2 rendering), `PromptFreezeTest` and `prompts/LOCK` (both
versions); the next live recording reports the cost by step.
