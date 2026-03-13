ALTER TABLE interview_sessions
    ADD COLUMN interview_mode VARCHAR(50);

UPDATE interview_sessions
SET interview_mode = 'free_interview'
WHERE interview_mode IS NULL;

ALTER TABLE interview_sessions
    ALTER COLUMN interview_mode SET NOT NULL;

CREATE TABLE interview_session_evidence_items (
    id BIGSERIAL PRIMARY KEY,
    interview_session_id BIGINT NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    section VARCHAR(50) NOT NULL,
    label VARCHAR(255),
    snippet TEXT NOT NULL,
    source_record_type VARCHAR(100) NOT NULL,
    source_record_id BIGINT NOT NULL,
    coverage_status VARCHAR(30) NOT NULL,
    coverage_priority INT NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_interview_session_evidence_items_session_order
    ON interview_session_evidence_items (interview_session_id, display_order, id);

CREATE INDEX idx_interview_session_evidence_items_session_status
    ON interview_session_evidence_items (interview_session_id, coverage_status, id);

CREATE TABLE interview_session_question_evidence_links (
    interview_session_question_id BIGINT NOT NULL REFERENCES interview_session_questions(id) ON DELETE CASCADE,
    interview_session_evidence_item_id BIGINT NOT NULL REFERENCES interview_session_evidence_items(id) ON DELETE CASCADE,
    link_role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (interview_session_question_id, interview_session_evidence_item_id)
);

CREATE INDEX idx_interview_session_question_evidence_links_evidence
    ON interview_session_question_evidence_links (interview_session_evidence_item_id, interview_session_question_id);
