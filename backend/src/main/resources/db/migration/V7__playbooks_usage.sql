-- P7 (architecture §11): playbooks, daily spend, cache usage of a run. V1 to V6 stay unedited.
-- No contract text and no personal data in any column (R-02).

CREATE TABLE daily_spend (
    day             DATE PRIMARY KEY,
    cost_usd_micros BIGINT NOT NULL DEFAULT 0 CHECK (cost_usd_micros >= 0)
);

ALTER TABLE analysis_run
    ADD COLUMN cache_write_tokens BIGINT NOT NULL DEFAULT 0;

ALTER TABLE analysis_trace
    ADD COLUMN cache_read_tokens  BIGINT,
    ADD COLUMN cache_write_tokens BIGINT;

CREATE TABLE playbook (
    id          UUID PRIMARY KEY,
    org_id      UUID NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    name        TEXT NOT NULL CHECK (char_length(name) BETWEEN 2 AND 80),
    side        TEXT NOT NULL CHECK (side IN ('WORKER', 'EMPLOYER')),
    version     INT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ
);

CREATE INDEX playbook_org_idx ON playbook (org_id, archived_at);

-- Policy rules of an organisation (FR-57). Messages are written by the organisation, not taken
-- from a contract; no contract text and no personal data (R-02).
CREATE TABLE playbook_rule (
    id                  UUID PRIMARY KEY,
    playbook_id         UUID NOT NULL REFERENCES playbook (id) ON DELETE CASCADE,
    ordinal             INT NOT NULL,
    clause_type         TEXT NOT NULL,
    fact                TEXT NOT NULL,
    comparator          TEXT NOT NULL CHECK (comparator IN ('MIN', 'MAX', 'EQ', 'REQUIRED', 'PROHIBITED')),
    value               DOUBLE PRECISION,
    unit                TEXT,
    severity            TEXT NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    message_bn          TEXT NOT NULL CHECK (char_length(message_bn) BETWEEN 1 AND 300),
    message_en          TEXT NOT NULL CHECK (char_length(message_en) BETWEEN 1 AND 300),
    statute_section_key TEXT,
    UNIQUE (playbook_id, ordinal)
);
