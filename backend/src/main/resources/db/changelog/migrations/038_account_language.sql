--liquibase formatted sql
--changeset plantpal:038-account-language
ALTER TABLE users ADD COLUMN language VARCHAR(8) NOT NULL DEFAULT 'en' CHECK (language IN ('en','fr','ar'));
CREATE INDEX idx_ai_translations_reuse ON ai_translations(owner_id, language, status);
