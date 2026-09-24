--liquibase formatted sql
--changeset plantpal:040-scan-content-language
ALTER TABLE identifications ADD COLUMN content_language VARCHAR(8)
 CHECK (content_language IN ('en','fr','ar'));
-- NULL means historical content: never infer a new generation language on old records.
