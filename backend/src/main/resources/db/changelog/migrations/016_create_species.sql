-- liquibase formatted sql
-- changeset plantpal:016

CREATE TABLE IF NOT EXISTS species (
  id BIGSERIAL PRIMARY KEY,
  scientific_name VARCHAR(255) NOT NULL UNIQUE,
  common_name VARCHAR(255),
  description TEXT,
  care_overview TEXT,
  image_url TEXT,
  external_data_source VARCHAR(20),
  external_data_fetched_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by VARCHAR(255),
  updated_by VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_species_scientific_name ON species(scientific_name);
