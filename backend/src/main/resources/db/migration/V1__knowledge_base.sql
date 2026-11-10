-- ShromikAI knowledge base schema
-- Laws are stored section by section, with versions, so only the current text is cited.

CREATE EXTENSION IF NOT EXISTS vector;

-- One row per Act (e.g. Bangladesh Labour Act 2006)
CREATE TABLE law_act (
    id            BIGSERIAL PRIMARY KEY,
    code          TEXT NOT NULL UNIQUE,          -- short id, e.g. 'BLA-2006', 'CA-1872'
    title_en      TEXT NOT NULL,
    title_bn      TEXT,
    year          INT,
    jurisdiction  TEXT NOT NULL DEFAULT 'BD',    -- jurisdiction pack, e.g. 'BD', 'ILO'
    source_url    TEXT NOT NULL,                 -- official source page
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per section version. Amended sections keep their old versions with is_current = false.
CREATE TABLE law_section (
    id              BIGSERIAL PRIMARY KEY,
    act_id          BIGINT NOT NULL REFERENCES law_act(id) ON DELETE CASCADE,
    section_no      TEXT NOT NULL,               -- '2', '26', '27(1)'
    heading_en      TEXT,
    heading_bn      TEXT,
    text_en         TEXT,
    text_bn         TEXT,
    version_label   TEXT NOT NULL DEFAULT 'original',   -- 'original', 'amend-2013', 'amend-2018'
    effective_from  DATE,
    is_current      BOOLEAN NOT NULL DEFAULT true,
    source_url      TEXT NOT NULL,
    embedding       vector(1024),                -- bge-m3 multilingual embedding
    search_tsv      tsvector GENERATED ALWAYS AS (
                        to_tsvector('simple',
                            coalesce(heading_en, '') || ' ' || coalesce(text_en, '') || ' ' ||
                            coalesce(heading_bn, '') || ' ' || coalesce(text_bn, ''))
                    ) STORED,
    UNIQUE (act_id, section_no, version_label)
);

CREATE INDEX law_section_current_idx ON law_section (act_id, section_no) WHERE is_current;
CREATE INDEX law_section_tsv_idx ON law_section USING GIN (search_tsv);
CREATE INDEX law_section_embedding_idx ON law_section USING hnsw (embedding vector_cosine_ops);

-- Graph of relations between sections: definitions, cross-references, amendments.
CREATE TABLE section_edge (
    from_section_id BIGINT NOT NULL REFERENCES law_section(id) ON DELETE CASCADE,
    to_section_id   BIGINT NOT NULL REFERENCES law_section(id) ON DELETE CASCADE,
    kind            TEXT NOT NULL CHECK (kind IN ('USES_DEFINITION', 'REFERS_TO', 'AMENDED_BY')),
    PRIMARY KEY (from_section_id, to_section_id, kind)
);

-- Explainability trace for each analysis. Never stores contract text (privacy rule).
CREATE TABLE analysis_trace (
    id            BIGSERIAL PRIMARY KEY,
    analysis_id   UUID NOT NULL,
    step          TEXT NOT NULL,               -- 'classify', 'retrieve', 'risk', 'verify', 'memo'
    detail        JSONB NOT NULL,              -- section ids, verification results, timings
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX analysis_trace_analysis_idx ON analysis_trace (analysis_id);

-- Risks the verifier could not support with a verified citation go to human review.
CREATE TABLE review_item (
    id            BIGSERIAL PRIMARY KEY,
    analysis_id   UUID NOT NULL,
    clause_type   TEXT NOT NULL,
    risk_summary  TEXT NOT NULL,               -- PII-redacted summary, not the contract clause
    reason        TEXT NOT NULL,               -- why verification failed
    status        TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CONFIRMED', 'REJECTED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
