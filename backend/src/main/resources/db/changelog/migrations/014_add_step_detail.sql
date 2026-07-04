-- liquibase formatted sql
-- changeset plantpal:014

ALTER TABLE reminders ADD COLUMN step_detail TEXT;
ALTER TABLE reminders ADD COLUMN step_diagram_format VARCHAR(20);
ALTER TABLE reminders ADD COLUMN step_diagram_content TEXT;
