-- liquibase formatted sql

-- changeset plantpal:005-create-push-subscriptions
-- comment: Web Push VAPID subscriptions per user — one user can have multiple devices
CREATE TABLE IF NOT EXISTS push_subscriptions
(
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    endpoint   TEXT        NOT NULL,
    key_p256dh TEXT        NOT NULL,
    key_auth   TEXT        NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX IF NOT EXISTS idx_push_subscriptions_user_id ON push_subscriptions (user_id, enabled);
-- rollback DROP INDEX IF EXISTS idx_push_subscriptions_user_id; DROP TABLE IF EXISTS push_subscriptions;
