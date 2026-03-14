ALTER TABLE interview_session_questions
    ADD COLUMN skipped_at TIMESTAMPTZ;

CREATE INDEX idx_interview_session_questions_skipped_at
    ON interview_session_questions (interview_session_id, skipped_at);
