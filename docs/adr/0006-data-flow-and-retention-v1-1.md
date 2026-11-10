# ADR-0006: Data-flow and retention corrections (spec v1.1)

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | ADR-0003 (local PII redaction); rules R-02, R-03; PRD FR-22, FR-30, FR-32, FR-50, FR-55, FR-58, FR-61, FR-64, FR-66; architecture §7.7, §8.2, §10.7, §14, §17, §18.3; api.md §2, §4.2, §9 |

## Context

The independent Phase 0 review of `spec-v1` found five places where the specification left open
what data goes where, or how long it stays:

1. The signed result envelope did not say whether it signs the redacted result held in the job
   store or the re-identified result the user receives. Clients build the envelope from the
   response, so signing the redacted form would make every real memo render fail.
2. SSE authentication was undefined. Browser `EventSource` cannot send an `Authorization` header,
   which invites tokens in query strings, where they leak into logs and history.
3. Free-text law search (`GET /api/v1/laws/search`, MCP `search_bd_law`) sent the user's query to
   `query-rewrite` on Claude Haiku without redaction, and MCP `verify_legal_citation` sent the
   caller's claim to the support model without redaction. Both contradict R-03.
4. `review_item.claimed_claim` holds text written by the risk agent, which may copy contract text,
   but only `risk_summary` had the 8-token overlap check.
5. Review tickets expired after 30 days while undecided review items lived 90 days, so decisions
   made after day 30 were invisible to the worker who asked for them.

These change what data is sent where and for how long (AGENTS.md §9, criterion 3).

## Decision

1. **The envelope signs the re-identified result.** `AnalysisService.resultFor` (module `analysis`) re-identifies the redacted result per response for the owning subject and signs that JSON tree (RFC 8785 + HMAC-SHA256) through `verify.SignedResultCodec`. The memo therefore shows the user's own names. The render request carries re-identified contract text in request memory only; it is never logged or stored, and Gotenberg uses `tmpfs`.
2. **SSE uses the Bearer header.** Browsers use a fetch-based SSE client written in the project; every endpoint, SSE included, accepts tokens only in the `Authorization` header, and requests carrying `access_token`, `token` or `api_key` query parameters are rejected with `400`.
3. **Free-text search makes no cloud call.** Search runs local embedding, full-text search and graph expansion on the query as written; `query-rewrite` runs only on redacted clauses inside an analysis. `rewrittenQueries` is removed from search responses.
4. **MCP claims are redacted locally.** `verify_legal_citation` runs the claim (now 20 to 600 characters, like every `ClaimedCitation`) through `analysis.ClaimRedactionService` (detectors and `pii-extract` on Ollama) before check (c); the egress guard checks the outgoing payload; if Ollama is down the call fails with `local-model-unavailable`.
5. **Every reviewer-visible text passes the 8-token check.** `ReviewItemFactory` applies `OverlapChecker` to `claimed_claim` as well as `risk_summary`; an overlapping claim is replaced by a facts-only form and flagged `claimReduced`.
6. **Review retention follows the ticket.** `FINDING` review items expire 30 days after creation while undecided and 30 days after their decision; a ticket stays valid until its last item expires. `CORPUS_CHANGE` items do not expire while undecided and are kept 12 months after decision.

## Consequences

Positive:

- Each path that takes user text now has a stated privacy treatment and a test (`NoPiiLeavesTheBoxIT` covers search and MCP claims; `NoContractPersistenceIT` scans 8-token windows in every column).
- Memos rendered from real responses work and are tested end to end (`MemoFromRealResponseIT`).
- No credential appears in a URL anywhere in the system.
- Undecided review items leave the database after 30 days instead of 90 (less data at rest).

Negative:

- Free-text search loses query rewriting, so a colloquial query may rank worse than in the P4 "hybrid + graph + rewrite" ablation, which measures clause retrieval, not free-text search. The P4 report states this limitation.
- An MCP `verify_legal_citation` call now costs a local model call and depends on Ollama.
- The project owns a small SSE client instead of using the browser's `EventSource`.
- An admin who decides a review item late (after day 30) can no longer reach the worker; the queue shows the expiry date so reviewers prioritise.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Sign the redacted result and re-identify in the renderer | The renderer would need the placeholder map, which lives only in the job and expires after 30 minutes, while the envelope is valid 24 hours |
| Single-use SSE stream tickets in the query string | Adds server state and still puts a secret in a URL |
| Redact free-text search queries locally, then rewrite in the cloud | A `pii-extract` call per search does not fit the NFR-02 budget (p95 ≤ 500 ms with 20 concurrent users) |
| Drop the claim from review items | Reviewers need to know what the agent claimed; the facts-only form keeps that without contract text |
| Keep 90-day review items and extend tickets to 90 days | Keeps redacted analysis metadata longer than the worker's realistic interest in the answer |

## Verification

- `MemoFromRealResponseIT`, `MemoEnvelopeTest`, `MemoTemplateTest` (FR-55).
- `AnalysesApiIT` (Bearer-only SSE, query-string token rejected) and `sse.test.ts` (FR-58).
- `LawApiIT` (zero `ChatPort` calls), `NoPiiLeavesTheBoxIT` (search and MCP claim paths), `FailClosedIT` (FR-22, FR-30, FR-31, FR-64).
- `ReviewItemFactoryTest`, `NoContractPersistenceIT` (FR-32, FR-50).
- `ReviewTicketIT`, `RetentionJobIT` (FR-61, NFR-16).
