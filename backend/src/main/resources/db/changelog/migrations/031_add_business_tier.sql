--liquibase formatted sql

--changeset plantpal:031_add_business_tier
--comment: PP-086 — self-declared business/professional tier flag (D027). No server-side
--  enforcement; the N=10 plants upgrade prompt threshold is frontend-only.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_business_tier BOOLEAN NOT NULL DEFAULT FALSE;
