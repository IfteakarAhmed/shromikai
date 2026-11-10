-- P2 identity (architecture §11.2, §11.3). Demo accounts are seeded at runtime by the demo
-- profile, never here. No table holds contract text.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE organisation (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
    kind       TEXT        NOT NULL CHECK (kind IN ('SME', 'HR', 'NGO', 'LAW_FIRM', 'JOB_PORTAL', 'PLATFORM')),
    plan       TEXT        NOT NULL CHECK (plan IN ('FREE', 'TEAM', 'API', 'LEGAL_AID')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE app_user (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID        REFERENCES organisation (id),
    email         CITEXT      NOT NULL UNIQUE CHECK (char_length(email) <= 254),
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL CHECK (role IN ('WORKER', 'HR', 'ADMIN')),
    locale        TEXT        NOT NULL DEFAULT 'bn' CHECK (locale IN ('bn', 'en')),
    profile       JSONB       NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(profile) = 'object'),
    is_demo       BOOLEAN     NOT NULL DEFAULT false,
    failed_logins INT         NOT NULL DEFAULT 0 CHECK (failed_logins >= 0),
    locked_until  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    disabled_at   TIMESTAMPTZ
);

CREATE INDEX app_user_org_idx ON app_user (org_id);

-- Refresh tokens are stored as SHA-256 only. A rotated token keeps its row (revoked_at,
-- replaced_by) so that reuse of an old token can revoke the whole family.
CREATE TABLE refresh_token (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id    UUID        NOT NULL,
    token_sha256 CHAR(64)    NOT NULL UNIQUE CHECK (token_sha256 ~ '^[0-9a-f]{64}$'),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);
