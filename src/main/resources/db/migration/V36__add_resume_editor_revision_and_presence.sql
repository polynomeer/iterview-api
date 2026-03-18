ALTER TABLE resume_editor_workspaces
    ADD COLUMN revision_no INT NOT NULL DEFAULT 1;

CREATE TABLE resume_editor_workspace_revisions (
    id BIGSERIAL PRIMARY KEY,
    resume_editor_workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    change_source VARCHAR(64) NOT NULL,
    change_summary_json TEXT NOT NULL,
    markdown_source TEXT,
    document_json TEXT NOT NULL,
    layout_metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_resume_editor_workspace_revisions_workspace
        FOREIGN KEY (resume_editor_workspace_id) REFERENCES resume_editor_workspaces (id),
    CONSTRAINT fk_resume_editor_workspace_revisions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_resume_editor_workspace_revisions_resume_version
        FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id),
    CONSTRAINT uq_resume_editor_workspace_revisions_workspace_revision
        UNIQUE (resume_editor_workspace_id, revision_no)
);

CREATE INDEX idx_resume_editor_workspace_revisions_workspace_id
    ON resume_editor_workspace_revisions (resume_editor_workspace_id, revision_no DESC);

CREATE TABLE resume_editor_presence_sessions (
    id BIGSERIAL PRIMARY KEY,
    resume_editor_workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_key VARCHAR(128) NOT NULL,
    view_mode VARCHAR(32),
    selected_block_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_resume_editor_presence_sessions_workspace
        FOREIGN KEY (resume_editor_workspace_id) REFERENCES resume_editor_workspaces (id),
    CONSTRAINT fk_resume_editor_presence_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_resume_editor_presence_sessions_workspace_session
        UNIQUE (resume_editor_workspace_id, session_key)
);

CREATE INDEX idx_resume_editor_presence_sessions_workspace_id
    ON resume_editor_presence_sessions (resume_editor_workspace_id, updated_at DESC);
