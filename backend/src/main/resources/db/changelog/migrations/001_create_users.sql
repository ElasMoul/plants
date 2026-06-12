-- liquibase formatted sql

-- changeset plantpal:001-create-users
-- comment: Initial users table with audit columns
CREATE TABLE IF NOT EXISTS users
(
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    CONSTRAINT uq_users_email UNIQUE (email)
);
-- rollback DROP TABLE IF EXISTS users;
