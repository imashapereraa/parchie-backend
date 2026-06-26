-- V4: User accounts + a tree of folders/documents per user.
--
-- Existing anonymous sessions stay anonymous: `sessions.owner_id` is nullable.
-- A `documents` row of kind='doc' has a `session_id` pointing at an existing
-- session — that session is what the Yjs collab service reads/writes. Owner
-- means the session never expires (collab service treats expires_at=NULL as
-- "no TTL") so the document is permanent.
--
-- Tree shape: adjacency list (parent_id NULL = root). Folders and docs share
-- the same table because the navigation tree is uniform; `kind` discriminates.

-- ----- users ------------------------------------------------------------

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Usernames are case-insensitive: store lowercased + indexed.
CREATE UNIQUE INDEX uq_users_username_lower ON users (LOWER(username));


-- ----- auth_tokens ------------------------------------------------------
-- Opaque bearer tokens. Single-row-per-token; on logout we delete the row.
-- A periodic sweep can drop expired rows; logging in many devices issues many
-- tokens (one per session), so we keep them small.

CREATE TABLE auth_tokens (
    token       VARCHAR(64)  PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_auth_tokens_user ON auth_tokens (user_id);
CREATE INDEX idx_auth_tokens_expires ON auth_tokens (expires_at);


-- ----- sessions.owner_id ------------------------------------------------
-- Sessions owned by a user are permanent. The collab service's TTL cleanup
-- ignores rows with expires_at IN THE FUTURE indefinitely, so we represent
-- "no expiry" as expires_at = a far-future timestamp; nullable owner_id is
-- the actual marker for "this is a permanent doc".

ALTER TABLE sessions
    ADD COLUMN owner_id UUID REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_sessions_owner ON sessions (owner_id);


-- ----- documents (tree) -------------------------------------------------
-- One row per node in the user's tree. Folders are containers; docs link to
-- a backing session for collab state. Soft constraints:
--   * kind='folder' must have session_id NULL
--   * kind='doc'    must have session_id NOT NULL
--   * parent_id, if set, must belong to the same owner_id (enforced at
--     application layer, not as a DB constraint, to keep the schema simple)

CREATE TABLE documents (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id   UUID         REFERENCES documents (id) ON DELETE CASCADE,
    kind        VARCHAR(16)  NOT NULL CHECK (kind IN ('folder', 'doc')),
    name        VARCHAR(255) NOT NULL,
    session_id  UUID         REFERENCES sessions (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_documents_session_per_kind CHECK (
        (kind = 'folder' AND session_id IS NULL) OR
        (kind = 'doc'    AND session_id IS NOT NULL)
    )
);

CREATE INDEX idx_documents_owner_parent ON documents (owner_id, parent_id);
CREATE INDEX idx_documents_session     ON documents (session_id);

-- Names are unique within a parent (per owner). Treating NULL parents as
-- "root" requires a partial index because NULL != NULL in unique constraints.
CREATE UNIQUE INDEX uq_documents_root_name
    ON documents (owner_id, LOWER(name))
    WHERE parent_id IS NULL;
CREATE UNIQUE INDEX uq_documents_child_name
    ON documents (owner_id, parent_id, LOWER(name))
    WHERE parent_id IS NOT NULL;
