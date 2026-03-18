CREATE TABLE resume_editor_workspaces (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL,
    workspace_status VARCHAR(32) NOT NULL,
    markdown_source TEXT,
    document_json TEXT NOT NULL,
    layout_metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_resume_editor_workspaces_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_resume_editor_workspaces_resume_version
        FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id),
    CONSTRAINT uq_resume_editor_workspaces_resume_version UNIQUE (resume_version_id)
);

CREATE INDEX idx_resume_editor_workspaces_user_id ON resume_editor_workspaces (user_id);

CREATE TABLE resume_editor_comment_threads (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_editor_workspace_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL,
    block_id VARCHAR(128) NOT NULL,
    field_path VARCHAR(255),
    selection_start_offset INT,
    selection_end_offset INT,
    selected_text TEXT,
    body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_resume_editor_comment_threads_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_resume_editor_comment_threads_workspace
        FOREIGN KEY (resume_editor_workspace_id) REFERENCES resume_editor_workspaces (id),
    CONSTRAINT fk_resume_editor_comment_threads_resume_version
        FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id)
);

CREATE INDEX idx_resume_editor_comment_threads_workspace_id
    ON resume_editor_comment_threads (resume_editor_workspace_id, created_at DESC);
CREATE INDEX idx_resume_editor_comment_threads_resume_version_id
    ON resume_editor_comment_threads (resume_version_id, status);

CREATE TABLE resume_editor_question_cards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_editor_workspace_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL,
    block_id VARCHAR(128) NOT NULL,
    field_path VARCHAR(255),
    selection_start_offset INT,
    selection_end_offset INT,
    selected_text TEXT,
    title VARCHAR(255),
    question_text TEXT NOT NULL,
    question_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    linked_question_id BIGINT,
    status VARCHAR(32) NOT NULL,
    follow_up_suggestions_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_resume_editor_question_cards_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_resume_editor_question_cards_workspace
        FOREIGN KEY (resume_editor_workspace_id) REFERENCES resume_editor_workspaces (id),
    CONSTRAINT fk_resume_editor_question_cards_resume_version
        FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id),
    CONSTRAINT fk_resume_editor_question_cards_linked_question
        FOREIGN KEY (linked_question_id) REFERENCES questions (id)
);

CREATE INDEX idx_resume_editor_question_cards_workspace_id
    ON resume_editor_question_cards (resume_editor_workspace_id, created_at DESC);
CREATE INDEX idx_resume_editor_question_cards_resume_version_id
    ON resume_editor_question_cards (resume_version_id, status);
