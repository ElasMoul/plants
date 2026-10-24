--liquibase formatted sql
--changeset plantpal:048-reminder-interval-override
-- PLA-108: optional user-set interval; NULL means no override. No backfill.
ALTER TABLE reminders ADD COLUMN override_frequency_days INTEGER;
