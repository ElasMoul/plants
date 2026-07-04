--liquibase formatted sql

--changeset plantpal:025_add_plantnet_preferences
--comment: T8.4 — per-user PlantNet flora (project) and common-name language preferences
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS plantnet_project VARCHAR(50) DEFAULT 'all',
    ADD COLUMN IF NOT EXISTS plantnet_lang    VARCHAR(10) DEFAULT 'en';
