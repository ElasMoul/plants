--liquibase formatted sql
--changeset plantpal:048-reminder-preferences
-- Per-user reminder delivery settings (PLA-122). Additive: rows are created lazily, and a missing
-- row means "push only, no quiet hours, not unsubscribed" (the behaviour before this change).
-- unsubscribe_token is the random, unguessable key behind the public email unsubscribe link.
CREATE TABLE reminder_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    channel VARCHAR(10) NOT NULL DEFAULT 'PUSH' CHECK (channel IN ('PUSH','EMAIL','BOTH')),
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    email_unsubscribed BOOLEAN NOT NULL DEFAULT FALSE,
    unsubscribe_token VARCHAR(64) NOT NULL UNIQUE,
    last_notified_on DATE
);
--rollback DROP TABLE reminder_preferences;
