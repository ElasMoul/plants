-- liquibase formatted sql
-- changeset plantpal:021

-- ai_model_preference is kept (not dropped this phase, deprecated only) — split into
-- two independent preferences: vision (identification/annotation) and reasoning
-- (care plans, cure advice, disease description, species enrichment).
ALTER TABLE users ADD COLUMN IF NOT EXISTS vision_model_preference VARCHAR(30) NOT NULL DEFAULT 'GITHUB_GPT4O';
ALTER TABLE users ADD COLUMN IF NOT EXISTS reasoning_model_preference VARCHAR(30) NOT NULL DEFAULT 'DEEPSEEK_R1';

UPDATE users
SET vision_model_preference = CASE
    WHEN ai_model_preference = 'OLLAMA_LLAVA' THEN 'OLLAMA_LLAVA'
    ELSE 'GITHUB_GPT4O'
END
WHERE ai_model_preference IN ('GITHUB_GPT4O', 'OLLAMA_LLAVA', 'PLANTNET');

UPDATE users
SET reasoning_model_preference = CASE
    WHEN ai_model_preference = 'OLLAMA_LLAVA' THEN 'OLLAMA_LLAVA'
    ELSE 'DEEPSEEK_R1'
END
WHERE ai_model_preference IN ('DEEPSEEK', 'OLLAMA_LLAVA');
