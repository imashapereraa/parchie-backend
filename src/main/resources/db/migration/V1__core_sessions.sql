-- V1: Core (no-signup experience)

CREATE TABLE sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ttl_days        INTEGER NOT NULL DEFAULT 7,
    is_locked       BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(72)
);

CREATE TABLE session_states (
    session_id      UUID PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE,
    encrypted_state BYTEA NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_expiry
    ON sessions (last_active_at, ttl_days);


-- ============================================================
-- Future migration sketch: Accounts + hosted pages (not yet active)
-- ============================================================

-- CREATE TABLE users (
--     id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     email           VARCHAR(255) NOT NULL UNIQUE,
--     password_hash   VARCHAR(72) NOT NULL,
--     display_name    VARCHAR(100),
--     created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
-- );
--
-- -- Link sessions to their creator (optional)
-- ALTER TABLE sessions
--     ADD COLUMN owner_id UUID REFERENCES users(id) ON DELETE SET NULL;
--
-- CREATE INDEX idx_sessions_owner ON sessions (owner_id) WHERE owner_id IS NOT NULL;
--
-- CREATE TABLE published_pages (
--     id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
--     session_id      UUID REFERENCES sessions(id) ON DELETE SET NULL,
--     slug            VARCHAR(100) NOT NULL,
--     title           VARCHAR(255),
--     html_content    TEXT NOT NULL,
--     is_public       BOOLEAN NOT NULL DEFAULT TRUE,
--     created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
--     updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
--
--     CONSTRAINT uq_user_slug UNIQUE (user_id, slug)
-- );
--
-- CREATE INDEX idx_published_pages_user ON published_pages (user_id);
