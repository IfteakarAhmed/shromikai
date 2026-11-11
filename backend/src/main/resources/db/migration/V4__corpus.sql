-- P3 statute corpus (architecture §11.2, §11.3, ADR-0011). V1 and V2 stay unedited.

ALTER TABLE law_act
    ADD COLUMN authoritative_lang  TEXT     NOT NULL DEFAULT 'bn'
        CONSTRAINT law_act_authoritative_lang_check CHECK (authoritative_lang IN ('bn', 'en')),
    ADD COLUMN status              TEXT     NOT NULL DEFAULT 'IN_FORCE'
        CONSTRAINT law_act_status_check CHECK (status IN ('IN_FORCE', 'REPEALED')),
    ADD COLUMN source_sha256       CHAR(64)
        CONSTRAINT law_act_source_sha256_hex CHECK (source_sha256 IS NULL OR source_sha256 ~ '^[0-9a-f]{64}$'),
    ADD COLUMN ratification_status TEXT;

ALTER TABLE law_section
    ADD COLUMN section_no_original TEXT,
    ADD COLUMN chapter             TEXT,
    ADD COLUMN parent_section_id   BIGINT REFERENCES law_section (id) ON DELETE SET NULL,
    ADD COLUMN fetched_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN normalised_text     TEXT,
    ADD COLUMN section_key         TEXT,
    -- Footnote text and kind the parser read for this version (ADR-0011 decision 6).
    ADD COLUMN amendment_notes     JSONB NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT law_section_amendment_notes_array CHECK (jsonb_typeof(amendment_notes) = 'array');

-- section_key is written by the ingestor; it cannot be a generated column because it reads
-- law_act.code. Backfill covers rows from before P3 (none exist in a fresh database).
UPDATE law_section s
SET section_key = a.code || ':' || s.section_no || '@' || s.version_label
FROM law_act a
WHERE a.id = s.act_id;

ALTER TABLE law_section
    ALTER COLUMN section_key SET NOT NULL,
    ADD CONSTRAINT law_section_section_key_unique UNIQUE (section_key),
    ADD CONSTRAINT law_section_section_key_pattern
        CHECK (section_key ~ '^[A-Za-z][A-Za-z0-9-]{1,31}:[0-9A-Za-z().-]{1,32}@[a-z0-9-]{1,32}$');

CREATE INDEX law_section_normalised_tsv_idx
    ON law_section USING GIN (to_tsvector('simple', coalesce(normalised_text, '')));

ALTER TABLE section_edge DROP CONSTRAINT section_edge_kind_check;
ALTER TABLE section_edge
    ADD CONSTRAINT section_edge_kind_check
        CHECK (kind IN ('USES_DEFINITION', 'REFERS_TO', 'AMENDED_BY', 'ALIGNS_WITH')),
    ADD COLUMN reviewed    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN reviewed_by TEXT,
    ADD COLUMN source      TEXT NOT NULL DEFAULT 'PARSER'
        CONSTRAINT section_edge_source_check CHECK (source IN ('PARSER', 'MAPPING'));

CREATE TABLE law_term (
    id         BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES law_section (id) ON DELETE CASCADE,
    kind       TEXT   NOT NULL CHECK (kind IN ('DEFINED_TERM', 'WORKER_CATEGORY')),
    code       TEXT   NOT NULL,
    term_bn    TEXT,
    term_en    TEXT,
    UNIQUE (kind, code)
);

CREATE INDEX law_term_section_idx ON law_term (section_id);

CREATE TABLE corpus_release (
    id            BIGSERIAL PRIMARY KEY,
    label         TEXT     NOT NULL UNIQUE
        CONSTRAINT corpus_release_label_pattern CHECK (label ~ '^[0-9]{4}\.[0-9]{2}\.[0-9]{2}-[0-9]+$'),
    sha256        CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    act_count     INT      NOT NULL,
    section_count INT      NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
