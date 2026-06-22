-- V2: Collapse session_states into sessions; replace ttl_days/last_active_at with expires_at.
-- See ADR-0005 for the single-blob storage rationale.

ALTER TABLE sessions
    ADD COLUMN encrypted_state BYTEA,
    ADD COLUMN expires_at      TIMESTAMPTZ;

UPDATE sessions s
   SET encrypted_state = ss.encrypted_state
  FROM session_states ss
 WHERE ss.session_id = s.id;

UPDATE sessions
   SET expires_at = last_active_at + (ttl_days || ' days')::interval
 WHERE expires_at IS NULL;

ALTER TABLE sessions
    ALTER COLUMN expires_at SET NOT NULL,
    ALTER COLUMN expires_at SET DEFAULT (now() + interval '7 days');

DROP INDEX idx_sessions_expiry;
DROP TABLE session_states;

ALTER TABLE sessions
    DROP COLUMN last_active_at,
    DROP COLUMN ttl_days;

CREATE INDEX idx_sessions_expires_at ON sessions (expires_at);