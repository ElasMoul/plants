-- liquibase formatted sql

-- changeset plantpal:023_add_plantnet_candidates
-- comment: PlantNet v2 ranked-candidate data on identifications (T8.1)

ALTER TABLE identifications
    ADD COLUMN IF NOT EXISTS plantnet_candidates        JSONB,
    ADD COLUMN IF NOT EXISTS plantnet_version          VARCHAR(50),
    ADD COLUMN IF NOT EXISTS plantnet_best_match       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS plantnet_switch_to_project VARCHAR(50),
    ADD COLUMN IF NOT EXISTS plantnet_quota_remaining   INT;
