-- liquibase formatted sql

-- changeset plantpal:026_add_plantnet_disease_crosscheck
-- comment: T8.5 — PlantNet disease/pest cross-check second-opinion columns

ALTER TABLE identifications
    ADD COLUMN IF NOT EXISTS plantnet_disease_results       JSONB,
    ADD COLUMN IF NOT EXISTS plantnet_disease_quota_remaining INT;

ALTER TABLE treatments
    ADD COLUMN IF NOT EXISTS plantnet_second_opinion JSONB,
    ADD COLUMN IF NOT EXISTS plantnet_agreement      BOOLEAN,
    ADD COLUMN IF NOT EXISTS eppo_code               VARCHAR(50),
    ADD COLUMN IF NOT EXISTS needs_review            BOOLEAN NOT NULL DEFAULT FALSE;
