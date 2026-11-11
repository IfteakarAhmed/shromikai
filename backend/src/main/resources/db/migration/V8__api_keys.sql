-- P8 (ADR-0021): API keys and usage events. V1 to V7 stay unedited.
-- No contract text and no personal data in any column (R-02). Only the SHA-256 of a key is stored.

CREATE TABLE api_key (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID NOT NULL REFERENCES organisation (id) ON DELETE CASCADE,
    prefix       CHAR(8) NOT NULL UNIQUE,
    key_sha256   CHAR(64) NOT NULL UNIQUE CHECK (key_sha256 ~ '^[0-9a-f]{64}$'),
    name         TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    scopes       TEXT[] NOT NULL CHECK (
                     cardinality(scopes) >= 1
                     AND scopes <@ ARRAY['analyses', 'laws', 'mcp', 'automation']::text[]),
    created_by   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX api_key_org_idx ON api_key (org_id, created_at);

CREATE TABLE usage_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID REFERENCES organisation (id) ON DELETE CASCADE,
    api_key_id      UUID REFERENCES api_key (id) ON DELETE SET NULL,
    user_id         UUID REFERENCES app_user (id) ON DELETE SET NULL,
    kind            TEXT NOT NULL CHECK (kind IN
                        ('ANALYSIS', 'COMPARE', 'MEMO_RENDER', 'API_CALL', 'MCP_CALL')),
    units           INT NOT NULL DEFAULT 1 CHECK (units >= 0),
    cost_usd_micros BIGINT NOT NULL DEFAULT 0 CHECK (cost_usd_micros >= 0),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX usage_event_org_idx ON usage_event (org_id, occurred_at);
