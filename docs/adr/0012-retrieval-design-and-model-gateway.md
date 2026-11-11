# ADR-0012: Retrieval design, embedding tracking and the model gateway

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-11 |
| Deciders | Team Leader (TODO: name), Backend/Data Engineer (TODO: name) |
| Related | FR-18 to FR-23; R-01, R-04, R-05, R-06, R-07; architecture §8; ADR-0004, ADR-0005, ADR-0006; P4 |

## Context

P4 adds embeddings, hybrid retrieval, graph expansion, query rewriting and the law API.
AGENTS.md §9 requires an ADR when a change adds a runtime dependency, changes what data is stored,
or adds to the model plumbing. P4 found five places where the specification needed a decision.

## Decision

1. **Caffeine** (managed by Spring Boot, 3.2.4) is added for the retrieval cache (FR-21). The cache
   holds *query embeddings* keyed by the normalised query (10,000 entries, 1 hour), so a repeated
   query does not call `EmbeddingPort`. Memory only: the keys may be user queries (architecture
   §10.7), so nothing reaches disk or a log.
2. **Migration V5** adds `embedded_hash`, `embedded_model` and `embedding_truncated` to
   `law_section`. `content_hash` says what the section contains; it cannot say which content the
   stored vector was computed from, nor which model produced it. A section is skipped only when
   its embedding exists, was produced by the current model and `embedded_hash = content_hash`.
   The embedding input is `"{heading_bn} | {heading_en}\n{authoritative text}"`, cut at 16,000
   characters (about 8,000 bge-m3 tokens of Bangla) and flagged when cut.
3. **The model gateway arrives in P4.** `query-rewrite` is the first runtime prompt, so
   `PromptRegistry` (the only reader of `prompts/`), `PromptRenderer` (escapes untrusted values)
   and `ModelGateway` (schema validation, one repair, role to provider and model) are built now in
   `shared.ai`, as the module map already lists. The cost ledger and `LlmCallListener` are not
   built yet; they arrive with the first multi-call phase (P6/P7). `query-rewrite` v1 has status
   `experimental` and is not in `LOCK` until a report is attached (AGENTS.md §7 item 6).
4. **Three algorithm clarifications** of architecture §8.2:
   - The full-text query is an **OR** query over the lexemes of the normalised query. A
     natural-language question contains words that a section lacks, so the AND query of
     `websearch_to_tsquery` returns almost nothing. The AND variant was *not* measured, so this is
     a reasoned choice; the measured weakness of the OR variant is in the retrieval report and the
     phase report.
   - Graph extras are ordered definitions first (`USES_DEFINITION`), then by the rank of the seed
     they came from, then by section ID. The SQL sketch ordered by edge kind name, which puts
     `REFERS_TO` first and lets cross-references crowd out the definitions FR-20 wants.
   - A topic-map key resolves to the *current* version of its act and section number.
5. **Evaluation design.** All five ablation rows go through `RetrievalService`, the production
   path. The topic map is off in every row because it was curated from the same headings the
   questions were written from. The rewrite row calls Claude Haiku only when `ANTHROPIC_API_KEY`
   is set and is otherwise printed as "not run"; nothing is simulated. The eval test lets
   `query-rewrite` pass the egress guard because its inputs are synthetic statute-derived
   questions, never user contract text; production keeps the fail-closed guard until P5.
6. **No tuning on the test set.** The 40 questions are the `retrieval` eval set (split `test`,
   evaluation.md §2). The first measured run exposed a weakness of the text list (see the phase
   report); it is documented with a proposed fix, not tuned away on the same questions.

## Consequences

- One new runtime dependency and one migration; `law_section` rows grow by about 80 bytes.
- Re-running `./gradlew embedLaws` is cheap when nothing changed and complete when the model or a
  section changed.
- Rewriting is fail-open to the raw clause by design, so a cloud outage or an egress block never
  stops retrieval.
- Until P5 installs the real egress guard, query rewriting always falls back to the raw clause in a
  running application (the guard blocks every cloud call).

## Alternatives considered

- Cache search results instead of embeddings: keys would need the filters and graph settings, and
  the embedding call is the slow part (about 100 ms against 5 ms of SQL).
- Embed on ingestion: couples statute ingestion to Ollama availability; a separate idempotent
  backfill keeps the P3 transaction independent.
- Weighted reciprocal-rank fusion: not adopted; it changes FR-19 and would need its own dev
  question set to tune.

## Verification

`EmbeddingBackfillIT`, `HybridSearchIT`, `GraphExpansionIT`, `GraphExpanderTest`, `TopicMapTest`,
`RetrievalCacheTest`, `QueryRewriteContractTest`, `ModelGatewayTest`, `PromptRendererTest`,
`LawApiIT`, `RetrievalEvalRunnerIT`; `./gradlew promptLint`; `eval/reports/retrieval.md`.
