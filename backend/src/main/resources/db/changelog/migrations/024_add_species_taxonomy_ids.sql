-- liquibase formatted sql

-- changeset plantpal:024_add_species_taxonomy_ids
-- comment: Factual taxonomy IDs from PlantNet v2 candidate results (T8.2) — GBIF, POWO, IUCN

ALTER TABLE species
    ADD COLUMN IF NOT EXISTS gbif_id       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS powo_id       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS iucn_category VARCHAR(10);
