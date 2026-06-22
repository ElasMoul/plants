-- liquibase formatted sql
-- changeset plantpal:022

ALTER TABLE treatments ADD COLUMN IF NOT EXISTS disease_description_model VARCHAR(30);
ALTER TABLE treatments ADD COLUMN IF NOT EXISTS treatment_plan_model VARCHAR(30);

ALTER TABLE species ADD COLUMN IF NOT EXISTS enrichment_model VARCHAR(30);
