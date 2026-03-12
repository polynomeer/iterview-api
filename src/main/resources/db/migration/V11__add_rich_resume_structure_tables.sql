ALTER TABLE resume_experience_snapshots
    ADD COLUMN company_name VARCHAR(255),
    ADD COLUMN role_name VARCHAR(255),
    ADD COLUMN employment_type VARCHAR(64),
    ADD COLUMN started_on DATE,
    ADD COLUMN ended_on DATE,
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE resume_profile_snapshots (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    full_name VARCHAR(255),
    headline VARCHAR(255),
    summary_text TEXT,
    location_text VARCHAR(255),
    years_of_experience_text VARCHAR(128),
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_resume_profile_snapshots_resume_version_id
    ON resume_profile_snapshots (resume_version_id);

CREATE TABLE resume_contact_points (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    contact_type VARCHAR(64) NOT NULL,
    label VARCHAR(255),
    value_text VARCHAR(512),
    url TEXT,
    display_order INT NOT NULL DEFAULT 1,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_contact_points_resume_version_id_display_order
    ON resume_contact_points (resume_version_id, display_order);

CREATE TABLE resume_competency_items (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    source_text TEXT,
    display_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_competency_items_resume_version_id_display_order
    ON resume_competency_items (resume_version_id, display_order);

CREATE TABLE resume_project_snapshots (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    resume_experience_snapshot_id BIGINT REFERENCES resume_experience_snapshots(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    organization_name VARCHAR(255),
    role_name VARCHAR(255),
    summary_text TEXT NOT NULL,
    tech_stack_text TEXT,
    started_on DATE,
    ended_on DATE,
    display_order INT NOT NULL DEFAULT 1,
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_project_snapshots_resume_version_id_display_order
    ON resume_project_snapshots (resume_version_id, display_order);

CREATE INDEX idx_resume_project_snapshots_experience_id
    ON resume_project_snapshots (resume_experience_snapshot_id);

CREATE TABLE resume_achievement_items (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    resume_experience_snapshot_id BIGINT REFERENCES resume_experience_snapshots(id) ON DELETE SET NULL,
    resume_project_snapshot_id BIGINT REFERENCES resume_project_snapshots(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    metric_text VARCHAR(255),
    impact_summary TEXT NOT NULL,
    source_text TEXT,
    severity_hint VARCHAR(32),
    display_order INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_achievement_items_resume_version_id_display_order
    ON resume_achievement_items (resume_version_id, display_order);

CREATE TABLE resume_education_items (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    institution_name VARCHAR(255) NOT NULL,
    degree_name VARCHAR(255),
    field_of_study VARCHAR(255),
    started_on DATE,
    ended_on DATE,
    description TEXT,
    display_order INT NOT NULL DEFAULT 1,
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_education_items_resume_version_id_display_order
    ON resume_education_items (resume_version_id, display_order);

CREATE TABLE resume_certification_items (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    issuer_name VARCHAR(255),
    credential_code VARCHAR(255),
    issued_on DATE,
    expires_on DATE,
    score_text VARCHAR(255),
    display_order INT NOT NULL DEFAULT 1,
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_certification_items_resume_version_id_display_order
    ON resume_certification_items (resume_version_id, display_order);

CREATE TABLE resume_award_items (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    issuer_name VARCHAR(255),
    awarded_on DATE,
    description TEXT,
    display_order INT NOT NULL DEFAULT 1,
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_award_items_resume_version_id_display_order
    ON resume_award_items (resume_version_id, display_order);
