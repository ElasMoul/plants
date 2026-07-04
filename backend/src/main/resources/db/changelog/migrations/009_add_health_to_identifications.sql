-- liquibase formatted sql

-- changeset plantpal:009-add-health-to-identifications
-- comment: Add health_status and health_notes for DeepSeek visual health analysis
ALTER TABLE identifications ADD COLUMN health_status VARCHAR(30);
ALTER TABLE identifications ADD COLUMN health_notes TEXT;
-- rollback ALTER TABLE identifications DROP COLUMN health_notes; ALTER TABLE identifications DROP COLUMN health_status;
