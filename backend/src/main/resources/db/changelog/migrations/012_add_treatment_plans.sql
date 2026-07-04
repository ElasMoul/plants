-- liquibase formatted sql
-- changeset plantpal:012

CREATE TABLE treatment_plans (
    id BIGSERIAL PRIMARY KEY,
    plant_id BIGINT NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    source_care_card_type VARCHAR(30),
    diagram_format VARCHAR(20),
    diagram_content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_treatment_plans_plant_id ON treatment_plans(plant_id);

ALTER TABLE reminders ADD COLUMN recurring BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE reminders ADD COLUMN treatment_plan_id BIGINT REFERENCES treatment_plans(id) ON DELETE CASCADE;
ALTER TABLE reminders ADD COLUMN treatment_plan_title VARCHAR(255);
ALTER TABLE reminders ADD COLUMN step_order INT;
CREATE INDEX idx_reminders_treatment_plan_id ON reminders(treatment_plan_id);
