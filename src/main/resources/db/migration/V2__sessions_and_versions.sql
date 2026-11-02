-- From this migration on, this server owns the schema. The Ktor server keeps working on it: Flyway ignores migrations
-- it does not know by default, and every new column has a default for the rows it writes.

-- A login starts a session (family); each refresh replaces the session's token. Rotated tokens are remembered until
-- they would have expired, so presenting one again reveals a stolen copy and revokes the whole session.
ALTER TABLE refresh_tokens
    ADD COLUMN family_id  UUID                     NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE used_refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  UUID                     NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_used_refresh_tokens_user ON used_refresh_tokens (user_id);
CREATE INDEX idx_used_refresh_tokens_expires ON used_refresh_tokens (expires_at);

-- Optimistic locking: every update increments it, and a client sends the version it edited as If-Match.
ALTER TABLE todos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
