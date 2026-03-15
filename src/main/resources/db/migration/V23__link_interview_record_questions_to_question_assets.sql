ALTER TABLE interview_record_questions
    ADD COLUMN linked_question_id BIGINT REFERENCES questions(id);

CREATE INDEX idx_interview_record_questions_linked_question_id
    ON interview_record_questions (linked_question_id);
