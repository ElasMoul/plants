-- liquibase formatted sql

-- changeset plantpal:007
ALTER TABLE identifications ADD COLUMN IF NOT EXISTS annotation_regions JSONB;
