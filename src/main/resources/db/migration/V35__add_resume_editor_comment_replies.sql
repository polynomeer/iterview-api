CREATE TABLE resume_editor_comment_replies (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_editor_comment_thread_id BIGINT NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_resume_editor_comment_replies_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_resume_editor_comment_replies_thread
        FOREIGN KEY (resume_editor_comment_thread_id) REFERENCES resume_editor_comment_threads (id)
);

CREATE INDEX idx_resume_editor_comment_replies_thread_id
    ON resume_editor_comment_replies (resume_editor_comment_thread_id, created_at ASC);
