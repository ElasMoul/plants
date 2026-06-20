-- liquibase formatted sql
-- changeset plantpal:017

ALTER TABLE plants
  ADD COLUMN IF NOT EXISTS species_id BIGINT REFERENCES species(id),
  ADD COLUMN IF NOT EXISTS last_scan_id BIGINT REFERENCES identifications(id),
  ADD COLUMN IF NOT EXISTS active_treatment_id BIGINT;

-- active_treatment_id deliberately has NO inline FK here: the `treatments` table doesn't exist
-- until migration 018, which (per the Phase 6 changelog ordering: 016 -> 017 -> 018 -> 019) runs
-- AFTER this migration. The FK constraint is added in migration 019 instead, once `treatments`
-- exists — see that file's trailing ALTER TABLE.

-- The legacy `species` TEXT column is deliberately NOT dropped here — existing rows keep their
-- free-text species name as a fallback display value until every Plant has been backfilled with
-- a speciesId via a re-scan. Drop it in a LATER cleanup migration once speciesId backfill is
-- confirmed complete in prod, not here.

CREATE INDEX IF NOT EXISTS idx_plants_species_id ON plants(species_id);
