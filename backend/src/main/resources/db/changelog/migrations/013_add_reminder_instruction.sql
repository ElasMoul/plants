-- liquibase formatted sql
-- changeset plantpal:013

ALTER TABLE reminders ADD COLUMN instruction TEXT;
