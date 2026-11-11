-- P6 intelligence core (architecture §11.2 and §11.3, conflicts C13, C14, C26). V1 to V5 stay
-- unedited. Every table here holds metadata only: no contract text, no personal data (R-02).

-- Registry of backend instances, for job forwarding (architecture §16.2); written from P7.
CREATE TABLE instance_registry (
    instance_id   TEXT PRIMARY KEY,
    internal_url  TEXT NOT NULL,
    heartbeat_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per analysis: who asked (as a hash), with which models and prompts, and what it cost.
CREATE TABLE analysis_run (
    id                        UUID PRIMARY KEY,
    instance_id               TEXT,
    subject_kind              TEXT NOT NULL CHECK (subject_kind IN ('GUEST', 'USER', 'API_KEY')),
    subject_hash              CHAR(64) NOT NULL CHECK (subject_hash ~ '^[0-9a-f]{64}$'),
    org_id                    UUID REFERENCES organisation (id) ON DELETE SET NULL,
    client_kind               TEXT NOT NULL CHECK (client_kind IN ('MOBILE_WEB', 'DESKTOP_WEB', 'API', 'MCP')),
    side                      TEXT NOT NULL CHECK (side IN ('WORKER', 'EMPLOYER')),
    output_language           TEXT NOT NULL CHECK (output_language IN ('bn', 'en')),
    input_kind                TEXT NOT NULL CHECK (input_kind IN ('PDF_TEXT', 'PDF_SCAN', 'IMAGE', 'TEXT')),
    page_count                INT NOT NULL CHECK (page_count >= 1),
    detected_language         TEXT CHECK (detected_language IN ('bn', 'en', 'mixed')),
    status                    TEXT NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    error_code                TEXT,
    finding_count             INT,
    verified_citation_count   INT,
    uncertain_finding_count   INT,
    corpus_release_id         BIGINT REFERENCES corpus_release (id),
    pipeline_version          TEXT NOT NULL,
    prompt_versions           JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_ids                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    cost_usd_micros           BIGINT NOT NULL DEFAULT 0,
    tokens_in                 BIGINT NOT NULL DEFAULT 0,
    tokens_out                BIGINT NOT NULL DEFAULT 0,
    cache_read_tokens         BIGINT NOT NULL DEFAULT 0,
    duration_ms               BIGINT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at              TIMESTAMPTZ
);

CREATE INDEX analysis_run_created_idx ON analysis_run (created_at);

-- analysis_trace (V1) was never written to; refuse to change it if someone did.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM analysis_trace) THEN
        RAISE EXCEPTION 'V6: analysis_trace is not empty; migrate its rows by hand first';
    END IF;
END $$;

ALTER TABLE analysis_trace
    ADD CONSTRAINT analysis_trace_run_fk
        FOREIGN KEY (analysis_id) REFERENCES analysis_run (id) ON DELETE CASCADE,
    ADD CONSTRAINT analysis_trace_step_stage CHECK (step IN
        ('INTAKE', 'OCR', 'REDACT', 'CLASSIFY', 'RETRIEVE', 'RULES', 'RISK', 'VERIFY', 'CORRECT',
         'COMPOSE', 'COMPLETE')),
    ADD CONSTRAINT analysis_trace_detail_small CHECK (pg_column_size(detail) <= 8192),
    ADD COLUMN prompt_id        TEXT,
    ADD COLUMN prompt_version   INT,
    ADD COLUMN model_id         TEXT,
    ADD COLUMN duration_ms      BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN tokens_in        BIGINT,
    ADD COLUMN tokens_out       BIGINT,
    ADD COLUMN cost_usd_micros  BIGINT,
    ADD COLUMN outcome          TEXT NOT NULL DEFAULT 'OK' CHECK (outcome IN ('OK', 'FAILED'));

-- review_item (V1) conflicts with the model of §11.2 (bigserial id against the API's UUID, NOT NULL
-- columns that CORPUS_CHANGE items cannot fill, a narrower status check). It was never written to.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM review_item) THEN
        RAISE EXCEPTION 'V6: review_item is not empty; migrate its rows by hand first';
    END IF;
END $$;

DROP TABLE review_item;

CREATE TABLE review_item (
    id                      UUID PRIMARY KEY,
    kind                    TEXT NOT NULL CHECK (kind IN ('FINDING', 'CORPUS_CHANGE')),
    analysis_id             UUID REFERENCES analysis_run (id) ON DELETE CASCADE,
    finding_id              TEXT,
    clause_type             TEXT,
    finding_kind            TEXT,
    severity                TEXT CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    side                    TEXT CHECK (side IN ('WORKER', 'EMPLOYER')),
    -- Redacted paraphrase; shares no 8-token sequence with the contract (FR-50).
    risk_summary            TEXT CHECK (char_length(risk_summary) <= 400),
    facts                   JSONB,
    claimed_section_key     TEXT,
    -- Statute text, not contract text.
    claimed_quote           TEXT CHECK (char_length(claimed_quote) <= 300),
    -- Redacted claim that passed the 8-token overlap check, or the facts-only form.
    claimed_claim           TEXT CHECK (char_length(claimed_claim) <= 600),
    claim_reduced           BOOLEAN NOT NULL DEFAULT false,
    reason_codes            TEXT[] NOT NULL,
    candidate_section_keys  TEXT[],
    corpus_change           JSONB,
    ticket_id               UUID,
    ticket_token_sha256     CHAR(64) CHECK (ticket_token_sha256 ~ '^[0-9a-f]{64}$'),
    assignee_id             UUID REFERENCES app_user (id) ON DELETE SET NULL,
    status                  TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED')),
    decision                TEXT CHECK (decision IN ('APPROVED_WITH_CITATION', 'APPROVED_WITHOUT_CITATION', 'REJECTED')),
    decision_section_key    TEXT,
    decision_quote          TEXT CHECK (char_length(decision_quote) <= 300),
    decision_note           TEXT CHECK (char_length(decision_note) <= 1000),
    decided_by              UUID REFERENCES app_user (id) ON DELETE SET NULL,
    decided_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT review_item_finding_fields
        CHECK (kind <> 'FINDING' OR (analysis_id IS NOT NULL AND finding_id IS NOT NULL AND clause_type IS NOT NULL)),
    CONSTRAINT review_item_corpus_change_fields
        CHECK (kind <> 'CORPUS_CHANGE' OR corpus_change IS NOT NULL)
);

CREATE INDEX review_item_analysis_idx ON review_item (analysis_id);
CREATE INDEX review_item_status_idx ON review_item (status, created_at);
