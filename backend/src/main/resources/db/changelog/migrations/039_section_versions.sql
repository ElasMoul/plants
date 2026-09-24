--liquibase formatted sql
--changeset plantpal:039-section-versions
CREATE TABLE plant_text_sections (
 id VARCHAR(64) PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id),
 kind VARCHAR(20) NOT NULL,
 resource_id BIGINT NOT NULL,
 section_key VARCHAR(80) NOT NULL,
 source_text TEXT NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,
 original_language VARCHAR(8) NOT NULL CHECK(original_language IN ('en','fr','ar')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE plant_text_versions (
 section_id VARCHAR(64) NOT NULL REFERENCES plant_text_sections(id),
 language VARCHAR(8) NOT NULL CHECK(language IN ('en','fr','ar')),
 fingerprint VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL,
 job_id VARCHAR(64) REFERENCES ai_translations(id),
 PRIMARY KEY(section_id,language)
);
CREATE INDEX idx_plant_text_sections_owner ON plant_text_sections(user_id,kind,resource_id);

CREATE TABLE generated_cure_advice (
 id BIGSERIAL PRIMARY KEY,
 identification_id BIGINT NOT NULL REFERENCES identifications(id),
 user_id BIGINT NOT NULL REFERENCES users(id),
 content JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
