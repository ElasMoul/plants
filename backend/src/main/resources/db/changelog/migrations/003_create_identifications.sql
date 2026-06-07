-- liquibase formatted sql

-- changeset plantpal:003-create-identifications
-- comment: AI identification results — raw_response stores full Ollama JSON for debugging
CREATE TABLE IF NOT EXISTS identifications
(
    id            BIGSERIAL   PRIMARY KEY,
    plant_id      BIGINT      REFERENCES plants (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    photo_url     TEXT        NOT NULL,
    raw_response  JSONB,
    species       VARCHAR(255),
    common_name   VARCHAR(255),
    confidence    VARCHAR(20),
    health_status VARCHAR(30),
    health_notes  TEXT,
    care_tips     JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    BIGINT
);

CREATE INDEX IF NOT EXISTS idx_identifications_plant_id ON identifications (plant_id);
CREATE INDEX IF NOT EXISTS idx_identifications_user_id  ON identifications (user_id);
-- rollback DROP INDEX IF EXISTS idx_identifications_user_id; DROP INDEX IF EXISTS idx_identifications_plant_id; DROP TABLE IF EXISTS identifications;
