--liquibase formatted sql
--changeset plantpal:044-identification-verdict
-- PLA-102: user verdict on an identification. Nullable; no backfill, NULL = no verdict.
ALTER TABLE identifications ADD COLUMN verdict VARCHAR(10);
ALTER TABLE identifications ADD COLUMN verdict_species VARCHAR(255);
ALTER TABLE identifications ADD COLUMN verdict_at TIMESTAMPTZ;
