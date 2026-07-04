-- liquibase formatted sql
-- changeset plantpal:019

-- plant_id was already nullable in the original 003 migration (no NOT NULL constraint was ever
-- applied to identifications.plant_id) — confirmed against that file before writing this, so no
-- DROP NOT NULL is needed here.
ALTER TABLE identifications ADD COLUMN IF NOT EXISTS species_id BIGINT REFERENCES species(id);

-- Deferred from migration 017: plants.active_treatment_id was added there as a plain BIGINT
-- (no inline FK) because the `treatments` table doesn't exist until migration 018, which runs
-- after 017. Now that 018 has run, add the FK constraint here.
-- Note: PostgreSQL has no "ADD CONSTRAINT IF NOT EXISTS" — each Liquibase changeset only ever
-- runs once (tracked in DATABASECHANGELOG), so plain ADD CONSTRAINT is safe and idempotent here.
ALTER TABLE plants
  ADD CONSTRAINT fk_plants_active_treatment
  FOREIGN KEY (active_treatment_id) REFERENCES treatments(id);
