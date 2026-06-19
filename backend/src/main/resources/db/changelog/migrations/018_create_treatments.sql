-- liquibase formatted sql
-- changeset plantpal:018

CREATE TABLE IF NOT EXISTS treatments (
  id BIGSERIAL PRIMARY KEY,
  plant_id BIGINT NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id),
  identification_id BIGINT REFERENCES identifications(id),
  disease_name VARCHAR(255) NOT NULL,
  disease_description TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  treatment_plan_id BIGINT REFERENCES treatment_plans(id),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_treatments_plant_id ON treatments(plant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_treatments_active_per_disease
  ON treatments(plant_id, disease_name) WHERE status IN ('DRAFT', 'IN_PROGRESS');
