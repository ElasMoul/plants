--liquibase formatted sql
--changeset plantpal:046-plant-journal-events
-- PLA-111: per-plant journal events (repotting, pruning, flowering, fruiting, propagation).
CREATE TABLE plant_journal_events (
    id         BIGSERIAL PRIMARY KEY,
    plant_id   BIGINT       NOT NULL REFERENCES plants(id),
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    type       VARCHAR(20)  NOT NULL,
    event_date DATE         NOT NULL,
    note       VARCHAR(1000),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE INDEX idx_plant_journal_events_plant_date ON plant_journal_events(plant_id, event_date);
--rollback DROP TABLE plant_journal_events;
