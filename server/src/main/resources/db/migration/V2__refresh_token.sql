-- FR-AUTH-02: refresh tokens with rotation and revocation.
-- Only the SHA-256 hash of the token value is stored.

CREATE TABLE refresh_token (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash   char(64)    NOT NULL,
    family_id    uuid        NOT NULL,
    issued_at    timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    revoked_at   timestamptz,
    replaced_by  uuid REFERENCES refresh_token (id)
);

CREATE UNIQUE INDEX refresh_token_hash_uq ON refresh_token (token_hash);
CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_expires_idx ON refresh_token (expires_at);
