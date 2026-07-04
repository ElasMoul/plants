--liquibase formatted sql

--changeset plantpal:029_add_generation_status
ALTER TABLE species    ADD COLUMN IF NOT EXISTS description_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE treatments ADD COLUMN IF NOT EXISTS description_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Backfill: rows that already have a description are READY; others stay PENDING
UPDATE species    SET description_status = 'READY' WHERE description IS NOT NULL AND description != '';
UPDATE treatments SET description_status = 'READY' WHERE disease_description IS NOT NULL AND disease_description != '';
--rollback ALTER TABLE species DROP COLUMN IF EXISTS description_status; ALTER TABLE treatments DROP COLUMN IF EXISTS description_status;
