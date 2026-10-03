--liquibase formatted sql
--changeset plantpal:041-identification-date-taken
ALTER TABLE identifications ADD COLUMN date_taken TIMESTAMPTZ;
-- Best-effort backfill: the original EXIF is not re-read, the upload time stands in.
UPDATE identifications SET date_taken = created_at WHERE date_taken IS NULL;
ALTER TABLE identifications ALTER COLUMN date_taken SET DEFAULT NOW();
ALTER TABLE identifications ALTER COLUMN date_taken SET NOT NULL;
