-- P4 retrieval (architecture §8.1, ADR-0012). V1 to V4 stay unedited.
-- Which content the stored embedding was computed from, so unchanged sections are never
-- re-embedded (FR-18) and a model change re-embeds everything.

ALTER TABLE law_section
    ADD COLUMN embedded_hash        CHAR(64)
        CONSTRAINT law_section_embedded_hash_hex CHECK (embedded_hash IS NULL OR embedded_hash ~ '^[0-9a-f]{64}$'),
    ADD COLUMN embedded_model       TEXT,
    -- True when the embedding input was cut at the bge-m3 context limit.
    ADD COLUMN embedding_truncated  BOOLEAN NOT NULL DEFAULT false;
