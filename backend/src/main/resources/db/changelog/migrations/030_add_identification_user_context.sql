--liquibase formatted sql
--changeset plantpal:030 labels:phase10

ALTER TABLE identifications ADD COLUMN user_context TEXT;
