--liquibase formatted sql
--changeset plantpal:045-plant-groups
-- PLA-110: per-user plant groups. A plant belongs to at most one group; deleting a group only clears plants.group_id.
CREATE TABLE plant_groups (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_plant_groups_user_id ON plant_groups(user_id);

ALTER TABLE plants ADD COLUMN group_id BIGINT REFERENCES plant_groups(id) ON DELETE SET NULL;
CREATE INDEX idx_plants_group_id ON plants(group_id);
