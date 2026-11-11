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
    name        TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
    description TEXT CHECK (char_length(description) <= 500),
    active      BOOLEAN NOT NULL DEFAULT true,
    rules       JSONB NOT NULL,
    created_by  UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX playbook_org_idx ON playbook (org_id);
