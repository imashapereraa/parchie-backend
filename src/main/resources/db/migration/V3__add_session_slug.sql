-- V3: Add a server-issued slug so shared URLs resolve in any browser.
-- See .scratch/session-slug-id/issues/01-server-issued-slug.md

ALTER TABLE sessions
    ADD COLUMN slug VARCHAR(32);

ALTER TABLE sessions
    ADD CONSTRAINT uq_sessions_slug UNIQUE (slug);

CREATE INDEX idx_sessions_slug ON sessions (slug);
