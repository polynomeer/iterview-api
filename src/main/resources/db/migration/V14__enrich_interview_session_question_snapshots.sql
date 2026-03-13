ALTER TABLE interview_session_questions
    ADD COLUMN body_text TEXT,
    ADD COLUMN focus_skill_names_json TEXT,
    ADD COLUMN resume_context_summary TEXT,
    ADD COLUMN generation_rationale TEXT,
    ADD COLUMN generation_status VARCHAR(30) NOT NULL DEFAULT 'seeded',
    ADD COLUMN llm_model VARCHAR(100),
    ADD COLUMN llm_prompt_version VARCHAR(100);

CREATE INDEX idx_interview_session_questions_session_generation
    ON interview_session_questions(interview_session_id, generation_status, order_index);
