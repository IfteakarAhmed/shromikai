# ADR-0020: P7 decisions: obligations cap, new dependencies, memo caps, TTL and sync window

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-16 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | ADR-0005, ADR-0006, ADR-0016, ADR-0019; rules R-02, R-04, R-05; PRD FR-51 to FR-63; architecture §5.3 |

## Context

P7 adds the obligations checklist, the memo, the redline diff, PDF rendering and the public API.
Several choices differ from the plan in architecture §5.3 or add a dependency (AGENTS.md §9 items 1
and 5), so they are recorded here as they are made. Everything below was decided and tested offline;
no live call was made and no number below is a measurement of a live answer.

## Decision

1. **Obligations output cap is 3,500 tokens, not 1,500 (architecture §5.3).** The checklist is
   bounded in the schema to 6 items, each with an obligation of at most 100 characters, a trigger of
   at most 60, at most 3 clause IDs and at most one citation (quote at most 100, claim at most 80).
   `PromptAnswerBoundAuditTest` builds the largest valid answer (six Bangla-heavy items); it is
   about 2,600 tokens at the measured rates, so 1,500 would cut it and 3,500 leaves the 15% margin.
   The call runs beside the risk batches (off the critical path), timeout 40 s on Haiku. The
   §5.3 estimate of 12 s for it was a planning number for a smaller answer. If a live run shows
   answers far below the bound, lower the caps in a new version.
2. **New runtime dependency `io.github.java-diff-utils:java-diff-utils` 4.15** for the word-level
   redline (FR-52). Pure Java, no transitive dependencies, Apache-2.0. Tokens are Unicode letter
   runs with combining marks (a Bangla word stays whole) or single punctuation characters.
3. **A daily spend table** `daily_spend` (one row per Dhaka day, micro-US-dollars, no other data)
   backs the daily cap (FR-62). It is shared by all backend instances; a failed analysis adds its
   cost too. This changes what is stored (§9 item 3): money per day, no contract data.
4. **Obligations failure is not fatal.** A model failure leaves the checklist out with warning
   `obligations-unavailable`; an egress block or a spent budget still ends the job (R-03).
