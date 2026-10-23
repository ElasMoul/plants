--liquibase formatted sql
--changeset plantpal:048-email-verification
-- Email ownership verification (PLA-26). Additive only: no backfill, no NOT NULL on existing
-- rows. A NULL email_verified_at means "not proven", which is every existing user; nothing seen
-- proves an address, so none is treated as verified. Login and recovery do not read this column.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- One row per issued link. Only the SHA-256 of the token is stored, so a database read cannot
-- yield a usable link. `email` is the address the link proves; it must still equal the user's
-- current address when the link is used.
CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_email_verification_tokens_hash ON email_verification_tokens (token_hash);
CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id, created_at);

--rollback DROP TABLE email_verification_tokens;
--rollback ALTER TABLE users DROP COLUMN email_verified_at;
