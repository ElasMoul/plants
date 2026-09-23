--liquibase formatted sql
--changeset plantpal:036-ai-translations
CREATE TABLE ai_translations (
 id VARCHAR(64) PRIMARY KEY,
 owner_id BIGINT REFERENCES users(id),
 language VARCHAR(8) NOT NULL CHECK (language IN ('fr')),
 source_text TEXT NOT NULL,
 translated_text TEXT,
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','READY','FAILED')),
 attempt INTEGER NOT NULL DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_by VARCHAR(255),
 updated_by VARCHAR(255)
);
CREATE INDEX idx_ai_translations_owner ON ai_translations(owner_id);
