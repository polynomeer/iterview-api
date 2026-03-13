ALTER TABLE interview_session_questions
    ADD COLUMN content_locale VARCHAR(8);

CREATE INDEX idx_interview_session_questions_content_locale
    ON interview_session_questions (content_locale);
