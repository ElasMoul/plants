--liquibase formatted sql

--changeset plantpal:008-add-care-plan
ALTER TABLE identifications ADD COLUMN IF NOT EXISTS care_plan JSONB;

--rollback ALTER TABLE identifications DROP COLUMN IF EXISTS care_plan;
