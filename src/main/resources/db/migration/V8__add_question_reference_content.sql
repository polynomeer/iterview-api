ALTER TABLE learning_materials
ADD COLUMN description TEXT,
ADD COLUMN difficulty_level VARCHAR(30),
ADD COLUMN estimated_minutes INT,
ADD COLUMN is_official BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN display_order_hint INT;

ALTER TABLE question_learning_materials
ADD COLUMN display_order INT,
ADD COLUMN relationship_type VARCHAR(50),
ADD COLUMN label_override VARCHAR(255);

CREATE TABLE question_reference_answers (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    title VARCHAR(255) NOT NULL,
    answer_text TEXT NOT NULL,
    answer_format VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    target_role_id BIGINT REFERENCES job_roles(id),
    company_id BIGINT REFERENCES companies(id),
    is_official BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_question_reference_answers_question_display
    ON question_reference_answers (question_id, display_order, id);

CREATE INDEX idx_question_learning_materials_question_display
    ON question_learning_materials (question_id, display_order, learning_material_id);
