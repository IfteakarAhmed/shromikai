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
5. **Memo narrative caps.** `memo-bn` and `memo-en` v1 are bounded in the schema (summary at most
   600 characters, at most 5 steps of at most 140) with `max_output_tokens` 2,000 on Sonnet, effort
   low, timeout 40 s. The audit's largest answer (Bangla) fits with the 15% margin. Architecture
   §5.3 gave no cap for them.
6. **`baseline` v1 cap is 4,000 tokens.** At most 4 findings and 2 citations each, short texts. The
   audit first failed at a 3,500 cap (worst case 3,009 against 2,975 allowed), so the cap was
   raised before any live call; this is the audit doing its job (ADR-0019).
7. **The memo narrative is not fatal.** If `memo-*` cannot answer (invalid output, refusal,
   outage), the memo uses a fixed summary and fixed next steps built from counts only, and the
   result carries warning `memo-narrative-unavailable`. Architecture §5.5 listed these failures as
   stopping the job; stopping after the analysis was paid for helps nobody. Egress blocks and a
   spent budget still stop it.
8. **Memo module takes its own input records** (`MemoInput`), so it does not depend on `analysis`
   (AGENTS.md §4 matrix is unchanged). The composer refuses a finding that carries citation IDs
   without being `VERIFIED`.
9. **The 30 minutes run from completion (FR-54).** `JobStore` expired jobs 30 minutes after
   creation (P5 behaviour); a long analysis therefore lost up to 150 s of its life. Expiry is now
   30 minutes after `completedAt`, or after creation for a job that never completes (bounded by the
   150 s hard timeout). `JobStoreTest` and `AnalysisResultsTest` (controllable clock) cover 29 and
   31 minutes. The store also keeps `id → owner key` of expired or deleted jobs (at most 10,000,
   no contract data) so the owner gets `410 analysis-expired` and every other subject `404`.
10. **The envelope key is derived when none is set.** `shromikai.memo.envelope-key` (env
    `ENVELOPE_KEY`) is used if present; otherwise the key is HMAC-SHA256(JWT secret,
    "shromikai-envelope-key-v1"), so a deployment needs no extra secret. The MAC covers the RFC 8785
    canonical payload, the key ID and the expiry. Numbers are canonicalised as shortest plain
    decimals, which equals the RFC form for the integers and values like 0.8 the result holds; a
    payload with exponent-form doubles is not supported (documented limit).
