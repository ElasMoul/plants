-- liquibase formatted sql
-- changeset plantpal:011

ALTER TABLE identifications
    ADD COLUMN IF NOT EXISTS source_image_width  INT,
    ADD COLUMN IF NOT EXISTS source_image_height INT;
