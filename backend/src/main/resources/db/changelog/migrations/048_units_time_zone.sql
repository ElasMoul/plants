--liquibase formatted sql
--changeset plantpal:048-units-time-zone
-- PLA-132: optional, nullable. NULL unit_system = use the default (region, else metric);
-- NULL time_zone = UTC. No backfill; existing rows stay NULL.
ALTER TABLE users ADD COLUMN unit_system VARCHAR(10);
ALTER TABLE users ADD COLUMN time_zone VARCHAR(64);
