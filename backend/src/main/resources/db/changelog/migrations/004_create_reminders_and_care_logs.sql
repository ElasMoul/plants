-- liquibase formatted sql

-- changeset plantpal:004-create-reminders
-- comment: Care reminders — partial index on enabled=true keeps the scheduler query fast
CREATE TABLE IF NOT EXISTS reminders
(
    id             BIGSERIAL   PRIMARY KEY,
    plant_id       BIGINT      NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    care_type      VARCHAR(30) NOT NULL,
    frequency_days INT         NOT NULL,
    next_due_at    TIMESTAMPTZ NOT NULL,
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reminders_due
    ON reminders (next_due_at, enabled)
    WHERE enabled = TRUE;

CREATE INDEX IF NOT EXISTS idx_reminders_plant_id ON reminders (plant_id);
CREATE INDEX IF NOT EXISTS idx_reminders_user_id  ON reminders (user_id);

-- changeset plantpal:004-create-care-logs
-- comment: Immutable log of every care action performed — no updates, no deletes
CREATE TABLE IF NOT EXISTS care_logs
(
    id           BIGSERIAL   PRIMARY KEY,
    plant_id     BIGINT      NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    care_type    VARCHAR(30) NOT NULL,
    notes        TEXT,
    performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_care_logs_plant_id ON care_logs (plant_id, performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_care_logs_user_id  ON care_logs (user_id);
-- rollback DROP INDEX IF EXISTS idx_care_logs_user_id; DROP INDEX IF EXISTS idx_care_logs_plant_id; DROP TABLE IF EXISTS care_logs; DROP INDEX IF EXISTS idx_reminders_user_id; DROP INDEX IF EXISTS idx_reminders_plant_id; DROP INDEX IF EXISTS idx_reminders_due; DROP TABLE IF EXISTS reminders;
