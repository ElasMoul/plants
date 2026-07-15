--liquibase formatted sql

--changeset plantpal:032_add_perf_indexes
--comment: PP-090/T-DEPLOY.2 — targeted indexes for hot query paths identified during
--  performance hardening. Checked existing indexes first (see migrations 003/004/016/018):
--  reminders(next_due_at, enabled) WHERE enabled = true already exists as idx_reminders_due
--  (migration 004) and species(scientific_name) is already covered by both a UNIQUE
--  constraint and idx_species_scientific_name (migration 016) — neither is duplicated here.

-- identifications: latest-scan-per-plant lookups (dashboard, plant detail history)
CREATE INDEX IF NOT EXISTS idx_identifications_plant_id_created_at
    ON identifications (plant_id, created_at DESC);

-- care_logs: per-user care history feed, most-recent-first (only plant_id+performed_at
-- existed before — idx_care_logs_plant_id from migration 004)
CREATE INDEX IF NOT EXISTS idx_care_logs_user_id_performed_at
    ON care_logs (user_id, performed_at DESC);

-- treatments: "active treatments for this plant" / status-filtered lookups
CREATE INDEX IF NOT EXISTS idx_treatments_plant_id_status
    ON treatments (plant_id, status);

--rollback DROP INDEX IF EXISTS idx_treatments_plant_id_status; DROP INDEX IF EXISTS idx_care_logs_user_id_performed_at; DROP INDEX IF EXISTS idx_identifications_plant_id_created_at;
