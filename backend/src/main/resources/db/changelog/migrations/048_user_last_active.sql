--liquibase formatted sql
--changeset plantpal:048-user-last-active
-- Live-user presence (PLA-33). One nullable timestamp on the user row, refreshed by a throttled
-- heartbeat. No history, IP, user agent or route is stored; only an aggregate count is exposed.
ALTER TABLE users ADD COLUMN last_active_at TIMESTAMPTZ;
--rollback ALTER TABLE users DROP COLUMN last_active_at;
