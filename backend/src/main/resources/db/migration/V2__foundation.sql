-- P1 foundation (architecture §11.2, §11.3). V1 stays unedited.

-- Statute ingestion (P3) records how each section version was produced.
ALTER TABLE law_section
    ADD COLUMN content_hash   CHAR(64),
    ADD COLUMN parser_version TEXT;

ALTER TABLE law_section
    ADD CONSTRAINT law_section_content_hash_hex
        CHECK (content_hash IS NULL OR content_hash ~ '^[0-9a-f]{64}$');

-- Append-only, content-free audit trail (T-15). detail is schema-validated by the trace module.
CREATE TABLE audit_event (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id     UUID,
    actor_kind   TEXT        NOT NULL CHECK (actor_kind IN ('USER', 'GUEST', 'API_KEY', 'SYSTEM')),
    action       TEXT        NOT NULL CHECK (action IN (
                                 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'DEMO_LOGIN', 'ACCESS_DENIED',
                                 'PRIVACY_BLOCK', 'REVIEW_DECISION', 'ADMIN_ACTION',
                                 'API_KEY_CREATED', 'API_KEY_REVOKED')),
    target_type  TEXT,
    target_id    TEXT,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    detail       JSONB       NOT NULL DEFAULT '{}'::jsonb
                             CHECK (jsonb_typeof(detail) = 'object' AND pg_column_size(detail) <= 2048)
);

CREATE INDEX audit_event_occurred_idx ON audit_event (occurred_at);
CREATE INDEX audit_event_action_idx ON audit_event (action, occurred_at);

-- The application may only INSERT and SELECT. Rows are never updated; the daily retention job
-- (architecture §14) may delete rows older than 12 months, nothing else.
CREATE FUNCTION audit_event_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit_event is append-only';
    END IF;
    IF OLD.occurred_at >= now() - INTERVAL '12 months' THEN
        RAISE EXCEPTION 'audit_event rows younger than 12 months cannot be deleted';
    END IF;
    RETURN OLD;
END;
$$;

CREATE FUNCTION audit_event_guard_truncate() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event cannot be truncated';
END;
$$;

CREATE TRIGGER audit_event_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_guard();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_guard_truncate();
