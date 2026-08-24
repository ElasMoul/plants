--liquibase formatted sql

--changeset plantpal:033_switch_default_ai_models_to_claude
--comment: GitHub Models was retired upstream (models.inference.ai.azure.com is gone from
--  DNS; models.github.ai returns 410 retirement brownouts), which kills every
--  GITHUB_*-backed preference and DeepSeek-R1 (served via the same endpoint). Move
--  affected users to ANTHROPIC_CLAUDE; deliberate PLANTNET/OLLAMA choices are untouched.
UPDATE users
SET vision_model_preference = 'ANTHROPIC_CLAUDE'
WHERE vision_model_preference IN ('GITHUB_GPT4O', 'GITHUB_GPT41');

UPDATE users
SET reasoning_model_preference = 'ANTHROPIC_CLAUDE'
WHERE reasoning_model_preference IN ('DEEPSEEK_R1', 'GITHUB_O4_MINI', 'GITHUB_GPT41_MINI');

-- Column defaults from migration 021 still pointed at the retired models.
ALTER TABLE users ALTER COLUMN vision_model_preference SET DEFAULT 'ANTHROPIC_CLAUDE';
ALTER TABLE users ALTER COLUMN reasoning_model_preference SET DEFAULT 'ANTHROPIC_CLAUDE';
