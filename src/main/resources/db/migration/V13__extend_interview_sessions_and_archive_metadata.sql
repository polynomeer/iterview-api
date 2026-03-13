ALTER TABLE interview_session_questions
    ALTER COLUMN question_id DROP NOT NULL;

ALTER TABLE interview_session_questions
    ADD COLUMN parent_session_question_id BIGINT REFERENCES interview_session_questions(id),
    ADD COLUMN prompt_text TEXT,
    ADD COLUMN question_source_type VARCHAR(30) NOT NULL DEFAULT 'catalog_seed',
    ADD COLUMN is_follow_up BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN depth INT NOT NULL DEFAULT 0,
    ADD COLUMN category_name VARCHAR(100),
    ADD COLUMN tags_json TEXT;

CREATE INDEX idx_interview_session_questions_session_parent
    ON interview_session_questions(interview_session_id, parent_session_question_id);
CREATE INDEX idx_interview_session_questions_session_depth
    ON interview_session_questions(interview_session_id, depth, order_index);

ALTER TABLE user_question_progress
    ADD COLUMN source_type VARCHAR(30) DEFAULT 'practice',
    ADD COLUMN source_label VARCHAR(50) DEFAULT 'Practice',
    ADD COLUMN source_session_id BIGINT REFERENCES interview_sessions(id),
    ADD COLUMN source_session_question_id BIGINT REFERENCES interview_session_questions(id),
    ADD COLUMN is_follow_up BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_question_progress
SET source_type = 'practice',
    source_label = 'Practice'
WHERE source_type IS NULL;

CREATE INDEX idx_user_question_progress_user_source
    ON user_question_progress(user_id, source_type, archived_at DESC);
