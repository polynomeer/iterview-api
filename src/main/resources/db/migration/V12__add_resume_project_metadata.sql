ALTER TABLE resume_project_snapshots
    ADD COLUMN content_text TEXT,
    ADD COLUMN project_category_code VARCHAR(128),
    ADD COLUMN project_category_name VARCHAR(255);

CREATE INDEX idx_resume_project_snapshots_category_code
    ON resume_project_snapshots (project_category_code);

CREATE TABLE resume_project_tags (
    id BIGSERIAL PRIMARY KEY,
    resume_project_snapshot_id BIGINT NOT NULL REFERENCES resume_project_snapshots(id) ON DELETE CASCADE,
    tag_name VARCHAR(128) NOT NULL,
    tag_type VARCHAR(64),
    display_order INT NOT NULL DEFAULT 1,
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_project_tags_project_id_display_order
    ON resume_project_tags (resume_project_snapshot_id, display_order);
