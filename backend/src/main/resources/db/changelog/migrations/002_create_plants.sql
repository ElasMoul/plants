-- liquibase formatted sql

-- changeset plantpal:002-create-plants
-- comment: Plants table — one row per plant owned by a user
CREATE TABLE IF NOT EXISTS plants
(
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    nickname    VARCHAR(255) NOT NULL,
    species     VARCHAR(255),
    common_name VARCHAR(255),
    photo_url   TEXT,
    location    VARCHAR(100),
    notes       TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    acquired_at DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  BIGINT,
    updated_by  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_plants_user_id     ON plants (user_id);
CREATE INDEX IF NOT EXISTS idx_plants_user_status ON plants (user_id, status);
-- rollback DROP INDEX IF EXISTS idx_plants_user_status; DROP INDEX IF EXISTS idx_plants_user_id; DROP TABLE IF EXISTS plants;
