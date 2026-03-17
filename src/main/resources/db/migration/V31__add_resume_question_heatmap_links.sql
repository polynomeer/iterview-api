CREATE TABLE resume_question_heatmap_links (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions (id) ON DELETE CASCADE,
    interview_record_question_id BIGINT NOT NULL REFERENCES interview_record_questions (id) ON DELETE CASCADE,
    anchor_type VARCHAR(50) NOT NULL,
    anchor_record_id BIGINT,
    anchor_key VARCHAR(100),
    link_source VARCHAR(32) NOT NULL,
    confidence_score NUMERIC(5,4),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_resume_question_heatmap_links_question
    ON resume_question_heatmap_links (interview_record_question_id);

CREATE INDEX idx_resume_question_heatmap_links_version_active
    ON resume_question_heatmap_links (resume_version_id, active, anchor_type, anchor_record_id);
