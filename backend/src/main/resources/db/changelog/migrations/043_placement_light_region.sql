--liquibase formatted sql
--changeset plantpal:043-placement-light-region
-- PLA-107: optional, nullable. No default/backfill; existing rows stay NULL.
ALTER TABLE plants ADD COLUMN placement VARCHAR(20);
ALTER TABLE plants ADD COLUMN light_exposure VARCHAR(20);
ALTER TABLE users ADD COLUMN region_country VARCHAR(2);
ALTER TABLE users ADD COLUMN region_climate VARCHAR(20);
