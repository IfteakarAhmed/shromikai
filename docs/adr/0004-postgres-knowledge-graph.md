# ADR-0004: PostgreSQL-native knowledge graph instead of a separate graph database

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-11-10 |
| Deciders | Backend/Data Engineer (TODO: name), Team Leader (TODO: name) |
| Related | PRD FR-12 to FR-23, FR-36, NFR-17; architecture §8, §9, §11; playbook P3, P4 |

## Context

Legal reasoning over statutes needs more than similarity search: a section uses terms defined
elsewhere, refers to other sections, is amended by later acts, and aligns with ILO convention
articles. We need vector search (bge-m3), full-text search in Bangla and English, relational data
(versions, accounts, metadata) and graph traversal. Phase 1 already created `law_section` with an
embedding column and a `tsvector`, and `section_edge`, in PostgreSQL 17 with pgvector.

The graph is small: a few acts and conventions, thousands of sections and tens of thousands of
edges, traversed one hop at a time.

## Decision

Use PostgreSQL as the only data store for the knowledge base, with reviewed YAML for curated data:

- **One section = one chunk:** `law_section.embedding vector(1024)` with an HNSW index; unchanged `content_hash` means no re-embedding.
- **Full-text:** `tsvector` over `BanglaNormalizer` output.
- **Graph:** `section_edge(from_section_id, to_section_id, kind, reviewed)` with kinds `REFERS_TO`, `USES_DEFINITION`, `AMENDED_BY`, `ALIGNS_WITH`; one-hop expansion in SQL (at most 4 extra sections).
- **Fusion:** reciprocal-rank fusion (k = 60) over vector and text lists of all rewritten queries.
- **Curated data as reviewed files:** ILO alignment (`data/mappings/bd-ilo.yaml`, rows `reviewed: false` until a human sets `true`), topic map (`data/mappings/bd-topics.yaml`) and legal rules (`data/rules/bd-labour.yaml`) live in git with author and reviewer recorded; they are loaded into PostgreSQL (`ALIGNS_WITH` edges) or into memory (rules, topic map) at startup.
- **Corpus releases:** one consistent, hashed snapshot per successful ingestion.

## Consequences

Positive:

- One system to run, back up and secure; consistent snapshots across sections, edges and releases.
- Simple local development; jurisdiction packs load into the same tables (NFR-17).
- Curated legal data is reviewed in git like code, with history.

Negative:

- No built-in graph algorithms beyond traversal; not needed for v1.
- Whole-section embeddings blur very long sections; long sections are truncated at 8,000 tokens and listed in the quality report, and full-text search and graph expansion compensate (measured by the P4 ablation).
- The `simple` text configuration has no Bangla stemming; vector search covers morphological variants (M-15).

Revisit when: the edge count exceeds 1,000,000; graph or search p95 exceeds 100 ms on the
reference deployment; or the P4 ablation shows whole-section embeddings losing ≥ 0.10 hit@5
against a chunked variant.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Neo4j or another graph database | Second datastore to sync and secure for a small graph |
| Dedicated vector database (Qdrant, Weaviate, Milvus) | pgvector meets the scale; another service and sync job |
| Elasticsearch or OpenSearch | Better analysers but heavy to operate at this size |
| Sub-section chunks with their own embeddings | Playbook design is one section = one chunk; revisit only on ablation evidence |
| Legal rules in a database table edited through an admin UI | Harder to review; YAML in git gives author, reviewer and diff history |

## Verification

- `HybridSearchIT`, `GraphExpansionIT`, `IloMappingLoaderTest`, `RuleLoaderFailFastTest`, `IngestionIdempotencyIT`.
- `eval/reports/retrieval.md` ablation (hybrid + graph ≥ best single method on hit@5, or explained).
