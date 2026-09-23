--liquibase formatted sql
--changeset plantpal:037-arabic-translations
ALTER TABLE ai_translations DROP CONSTRAINT ai_translations_language_check;
ALTER TABLE ai_translations ADD CONSTRAINT ai_translations_language_check CHECK (language IN ('fr', 'ar'));
