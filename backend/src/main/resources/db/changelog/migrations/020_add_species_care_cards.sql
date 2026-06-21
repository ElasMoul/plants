-- liquibase formatted sql
-- changeset plantpal:020

ALTER TABLE species ADD COLUMN IF NOT EXISTS care_cards TEXT;
