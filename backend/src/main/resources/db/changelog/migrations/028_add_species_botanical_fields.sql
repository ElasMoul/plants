--liquibase formatted sql

--changeset plantpal:028_add_species_botanical_fields
ALTER TABLE species ADD COLUMN IF NOT EXISTS family VARCHAR(255);
ALTER TABLE species ADD COLUMN IF NOT EXISTS genus VARCHAR(255);
ALTER TABLE species ADD COLUMN IF NOT EXISTS image_attribution VARCHAR(255);
ALTER TABLE species ADD COLUMN IF NOT EXISTS image_license VARCHAR(64);
-- "PLANTNET" | "AI" | "MANUAL" — identity_source tracks where the primary image + taxonomy came from
ALTER TABLE species ADD COLUMN IF NOT EXISTS identity_source VARCHAR(20);
--rollback ALTER TABLE species DROP COLUMN IF EXISTS family; ALTER TABLE species DROP COLUMN IF EXISTS genus; ALTER TABLE species DROP COLUMN IF EXISTS image_attribution; ALTER TABLE species DROP COLUMN IF EXISTS image_license; ALTER TABLE species DROP COLUMN IF EXISTS identity_source;
