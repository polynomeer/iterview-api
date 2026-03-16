ALTER TABLE question_reference_answers
ADD COLUMN content_locale VARCHAR(10);

ALTER TABLE learning_materials
ADD COLUMN content_locale VARCHAR(10);

CREATE TABLE user_question_reference_answers (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    answer_text TEXT NOT NULL,
    answer_format VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    content_locale VARCHAR(10),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_question_reference_answers_question_user_display
    ON user_question_reference_answers (question_id, user_id, display_order, id);

CREATE TABLE user_question_learning_materials (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    description TEXT,
    content_text TEXT,
    content_url TEXT,
    source_name VARCHAR(255),
    difficulty_level VARCHAR(30),
    estimated_minutes INT,
    relationship_type VARCHAR(50),
    label_override VARCHAR(255),
    relevance_score NUMERIC(5,2),
    source_type VARCHAR(50) NOT NULL,
    content_locale VARCHAR(10),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_question_learning_materials_question_user_display
    ON user_question_learning_materials (question_id, user_id, display_order, id);
