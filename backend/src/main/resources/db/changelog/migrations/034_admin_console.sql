--liquibase formatted sql
--changeset plantpal:034-admin-console
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));

CREATE TABLE ai_model_settings (
    id VARCHAR(80) PRIMARY KEY,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE TABLE admin_activity (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(255) NOT NULL,
    target VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE INDEX idx_admin_activity_created ON admin_activity (created_at DESC);
INSERT INTO ai_model_settings (id, visible) VALUES
 ('VISION:GITHUB_GPT4O', true), ('VISION:GITHUB_GPT41', true),
 ('VISION:ANTHROPIC_CLAUDE', true), ('VISION:OLLAMA_GEMMA3', true), ('VISION:PLANTNET', true),
 ('REASONING:DEEPSEEK_R1', true), ('REASONING:GITHUB_O4_MINI', true),
 ('REASONING:GITHUB_GPT41_MINI', true), ('REASONING:ANTHROPIC_CLAUDE', true),
 ('REASONING:OLLAMA_GEMMA3', true);
