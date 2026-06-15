-- liquibase formatted sql
-- changeset plantpal:010

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ai_model_preference VARCHAR(50) NOT NULL DEFAULT 'DEEPSEEK';
