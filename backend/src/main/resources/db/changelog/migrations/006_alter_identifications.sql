-- liquibase formatted sql

-- changeset plantpal:006-alter-identifications
-- comment: Rebuild identifications table for PlantNet integration.
--          Replaces Ollama-era columns with PlantNet result fields.
--          raw_response changed from JSONB to TEXT to store arbitrary API JSON.
DROP TABLE IF EXISTS identifications CASCADE;

CREATE TABLE identifications
(
    id              BIGSERIAL        PRIMARY KEY,
    plant_id        BIGINT           REFERENCES plants (id) ON DELETE CASCADE,
    user_id         BIGINT           NOT NULL REFERENCES users (id),
    photo_url       TEXT,
    raw_response    TEXT,
    scientific_name VARCHAR(255),
    common_name     VARCHAR(255),
    confidence      DOUBLE PRECISION,
    status          VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_identifications_plant_id ON identifications (plant_id);
CREATE INDEX IF NOT EXISTS idx_identifications_user_id  ON identifications (user_id);
-- rollback DROP INDEX IF EXISTS idx_identifications_user_id; DROP INDEX IF EXISTS idx_identifications_plant_id; DROP TABLE IF EXISTS identifications;
