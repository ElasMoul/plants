-- liquibase formatted sql

-- changeset plantpal:027_add_identification_stage_status
-- comment: T8.A — per-stage status + model tracking on identifications (Phase 8.5)

ALTER TABLE identifications
    ADD COLUMN IF NOT EXISTS identification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS annotation_status     VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS candidate_status      VARCHAR(30) NOT NULL DEFAULT 'SKIPPED',
    ADD COLUMN IF NOT EXISTS identification_model  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS annotation_model      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS failure_reason        TEXT;

-- Backfill from the existing top-level status column.
-- After ADD COLUMN all rows have: identification_status='PENDING', annotation_status='PENDING',
-- candidate_status='SKIPPED'. Apply per-status corrections below.

-- COMPLETED rows: core + annotation both succeeded; PlantNet broken since Phase 8 → SKIPPED is honest
UPDATE identifications
SET identification_status = 'COMPLETED',
    annotation_status     = 'COMPLETED'
WHERE status = 'COMPLETED';

-- FAILED rows: core failed; enrichment stages were never attempted
UPDATE identifications
SET identification_status = 'FAILED',
    annotation_status     = 'SKIPPED'
WHERE status = 'FAILED';

-- PENDING rows: all three stages remain in-flight → flip candidate_status back to PENDING
-- (identification_status and annotation_status are already 'PENDING' from the DEFAULT)
UPDATE identifications
SET candidate_status = 'PENDING'
WHERE status = 'PENDING';
