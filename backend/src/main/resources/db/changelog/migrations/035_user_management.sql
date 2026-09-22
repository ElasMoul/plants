--liquibase formatted sql
--changeset plantpal:035-user-management
ALTER TABLE users ADD COLUMN max_plants INTEGER NOT NULL DEFAULT 100 CHECK(max_plants >= 0);
ALTER TABLE users ADD COLUMN daily_scan_limit INTEGER NOT NULL DEFAULT 20 CHECK(daily_scan_limit >= 0);
ALTER TABLE users ADD COLUMN daily_ai_limit INTEGER NOT NULL DEFAULT 100 CHECK(daily_ai_limit >= 0);
CREATE TABLE user_daily_usage (
 user_id BIGINT NOT NULL REFERENCES users(id),
 usage_date DATE NOT NULL,
 scans INTEGER NOT NULL DEFAULT 0,
 ai_requests INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_by VARCHAR(255), updated_by VARCHAR(255),
 PRIMARY KEY(user_id, usage_date)
);
INSERT INTO ai_model_settings(id) VALUES ('VISION:DEEPSEEK_FLASH'), ('REASONING:DEEPSEEK_FLASH');
