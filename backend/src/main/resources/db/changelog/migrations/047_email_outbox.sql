--liquibase formatted sql
--changeset plantpal:047-email-outbox
-- Transactional email outbox (PLA-25). A row is written in the caller's transaction and a
-- dispatcher sends it later, so a provider outage never fails a registration or recovery
-- request and a failed send is retried. `variables` holds template placeholders (which may
-- include a one-time link); it is blanked as soon as the row reaches SENT or FAILED.
CREATE TABLE email_outbox (
    id BIGSERIAL PRIMARY KEY,
    template VARCHAR(40) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    language VARCHAR(8) NOT NULL DEFAULT 'en',
    variables TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error VARCHAR(500),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE INDEX idx_email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_email_outbox_status_created ON email_outbox (status, created_at DESC);
