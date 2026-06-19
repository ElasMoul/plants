-- liquibase formatted sql
-- changeset plantpal:015

ALTER TABLE identifications ADD COLUMN ai_model_used VARCHAR(30);
