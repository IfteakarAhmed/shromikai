# ADR-0002: Verification-first citation design

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name), Business Analyst/Data Scientist (TODO: name) |
| Related | Rule R-01; PRD FR-35 to FR-50; architecture §7 to §9; playbook P6 |

## Context

Generic LLMs produce fluent legal answers with invented section numbers and quotes. For a worker
deciding whether to sign a contract, a confident false citation is worse than none. The challenge
asks for a "verified memorandum citing real statutes" and "hallucination-free citation".
ShromikAI's core promise is that every citation shown to a user has passed verification against
the official statute text, and that the system says "uncertain" when it cannot verify.

Playbook v1.0 included a `WEAK` status for partially supported citations and accepted "VERIFIED or
WEAK" citations in tests and the UI. A partially supported citation is not verified, so showing
it would break the core promise.

## Decision

Citations pass layered defences; any single layer may fail without a false citation reaching a user.

1. **Constrained citation space.** The risk agent may only cite section keys from the retrieved candidate set: the output schema's `sectionKey` is a per-request `enum`, and the existence check rejects keys outside the set (`NOT_IN_CANDIDATES`).
2. **Deterministic floor.** A rule engine checks typed clause facts against rules in reviewed YAML, whose quotes are checked against the corpus at startup (fail fast).
3. **Three checks per citation.** (a) existence and currency; (b) quote match against the authoritative text after `BanglaNormalizer` (20 to 300 characters; substring or Sellers similarity ≥ 0.92); (c) support by a different model family (Gemini Flash), with a separate Claude prompt as a fallback flagged `SAME_FAMILY`. Labels: `SUPPORTS`, `PARTIAL`, `CONTRADICTS`, `UNRELATED`.
4. **Decision table.** (a) and (b) pass + `SUPPORTS` with confidence ≥ 0.70 = `VERIFIED`; (a) and (b) pass + `PARTIAL`, low confidence or verifier unavailable = `UNCERTAIN`; anything else = `REJECTED`. There is no `WEAK` status.
5. **Corrective loop (agentic RAG).** A citation that is not verified triggers one query rewrite, a new retrieval and one repair request to the agent; if it still fails, the finding is shown as অনিশ্চিত / Uncertain without a citation and goes to the human review queue. Reviewers can attach a citation only if it passes existence and quote checks.
6. **Type-level guarantee.** Only `bd.shromikai.verify` can construct `VerifiedCitation`, the only citation type in user-facing schemas.
7. **Tamper-evident memos.** Memos are rendered from an HMAC-signed result envelope.
8. **Visible proof.** Compare mode runs a plain-LLM baseline beside ShromikAI and shows the verifier rejecting the baseline's invented citations.

## Consequences

Positive:

- M-01 (unverified-citation exposure) is 0 by construction; M-04 measures how well fabricated citations are caught; M-05 shows the verifier does not simply reject everything.
- Every verified citation is explainable in the "কেন?" drawer (checks passed, model that judged support).
- Abstention is a visible, honest behaviour with a path to resolution.

Negative:

- Extra latency and cost (support checks, corrective loop; about US$0.03 per analysis, PRD §9.1).
- Some true findings will be shown as uncertain (M-12 ceiling 0.30); thresholds change only through an ADR with eval evidence.
- Recall depends on retrieval; mitigated by query rewriting, hybrid search, graph expansion, the topic map and the corrective re-retrieval (M-15).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Prompt instructions only ("do not invent sections") | No guarantee; the failure is silent |
| A `WEAK` / partial status shown to users (playbook v1.0) | Shows citations that did not pass verification |
| Claude's document citations feature over retrieved sections | Cannot be combined with structured outputs (`output_config.format`) and does not check currency or support |
| Same-model LLM-as-judge only | Correlated errors; kept only as the flagged fallback |
| Human review of every finding | Does not scale to free worker use; used for uncertain findings only |

## Verification

- `VerificationDecisionTableTest`, `FakeCitationInjectionTest`, `CitationOutsideRetrievedSetTest`, `AllResponsesCarryOnlyVerifiedCitationsIT`, `ArchitectureTest`.
- Evaluation hard gates M-01 = 0 and M-04 ≥ 0.99; target M-05 ≥ 0.90.
