CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    normalized_name VARCHAR(200) NOT NULL UNIQUE,
    domain VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE job_roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    parent_role_id BIGINT REFERENCES job_roles(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    nickname VARCHAR(50),
    job_role_id BIGINT REFERENCES job_roles(id),
    years_of_experience INT,
    avg_score NUMERIC(5,2),
    archived_question_count INT NOT NULL DEFAULT 0,
    answer_visibility_default VARCHAR(30) NOT NULL DEFAULT 'private',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    target_score_threshold INT NOT NULL DEFAULT 80,
    pass_score_threshold INT NOT NULL DEFAULT 60,
    retry_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    daily_question_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_target_companies (
    user_id BIGINT NOT NULL REFERENCES users(id),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    priority_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, company_id)
);

CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE resume_versions (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL REFERENCES resumes(id),
    version_no INT NOT NULL,
    file_url TEXT,
    raw_text TEXT,
    parsed_json TEXT,
    summary_text TEXT,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (resume_id, version_no)
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    parent_id BIGINT REFERENCES categories(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    tag_type VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    author_user_id BIGINT REFERENCES users(id),
    category_id BIGINT NOT NULL REFERENCES categories(id),
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    difficulty_level VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    quality_status VARCHAR(30) NOT NULL,
    visibility VARCHAR(30) NOT NULL,
    expected_answer_seconds INT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE question_tags (
    question_id BIGINT NOT NULL REFERENCES questions(id),
    tag_id BIGINT NOT NULL REFERENCES tags(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (question_id, tag_id)
);

CREATE TABLE question_companies (
    question_id BIGINT NOT NULL REFERENCES questions(id),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    relevance_score NUMERIC(5,2) NOT NULL,
    is_past_frequent BOOLEAN NOT NULL DEFAULT FALSE,
    is_trending_recent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (question_id, company_id)
);

CREATE TABLE question_roles (
    question_id BIGINT NOT NULL REFERENCES questions(id),
    job_role_id BIGINT NOT NULL REFERENCES job_roles(id),
    relevance_score NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (question_id, job_role_id)
);

CREATE TABLE learning_materials (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    content_text TEXT,
    content_url TEXT,
    source_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE question_learning_materials (
    question_id BIGINT NOT NULL REFERENCES questions(id),
    learning_material_id BIGINT NOT NULL REFERENCES learning_materials(id),
    relevance_score NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (question_id, learning_material_id)
);

CREATE TABLE daily_cards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    question_id BIGINT NOT NULL REFERENCES questions(id),
    card_date DATE NOT NULL,
    card_type VARCHAR(30) NOT NULL,
    source_reason VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    delivered_at TIMESTAMPTZ,
    opened_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE answer_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    question_id BIGINT NOT NULL REFERENCES questions(id),
    resume_version_id BIGINT REFERENCES resume_versions(id),
    source_daily_card_id BIGINT REFERENCES daily_cards(id),
    attempt_no INT NOT NULL,
    answer_mode VARCHAR(30) NOT NULL,
    content_text TEXT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, question_id, attempt_no)
);

CREATE TABLE answer_scores (
    answer_attempt_id BIGINT PRIMARY KEY REFERENCES answer_attempts(id),
    total_score NUMERIC(5,2) NOT NULL,
    structure_score NUMERIC(5,2) NOT NULL,
    specificity_score NUMERIC(5,2) NOT NULL,
    technical_accuracy_score NUMERIC(5,2) NOT NULL,
    role_fit_score NUMERIC(5,2) NOT NULL,
    company_fit_score NUMERIC(5,2) NOT NULL,
    communication_score NUMERIC(5,2) NOT NULL,
    evaluation_result VARCHAR(30) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE answer_feedback_items (
    id BIGSERIAL PRIMARY KEY,
    answer_attempt_id BIGINT NOT NULL REFERENCES answer_attempts(id),
    feedback_type VARCHAR(50) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_question_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    question_id BIGINT NOT NULL REFERENCES questions(id),
    latest_answer_attempt_id BIGINT REFERENCES answer_attempts(id),
    best_answer_attempt_id BIGINT REFERENCES answer_attempts(id),
    latest_score NUMERIC(5,2),
    best_score NUMERIC(5,2),
    total_attempt_count INT NOT NULL DEFAULT 0,
    unanswered_count INT NOT NULL DEFAULT 0,
    current_status VARCHAR(30) NOT NULL,
    archived_at TIMESTAMPTZ,
    last_answered_at TIMESTAMPTZ,
    next_review_at TIMESTAMPTZ,
    mastery_level VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, question_id)
);

CREATE TABLE review_queue (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    question_id BIGINT NOT NULL REFERENCES questions(id),
    trigger_answer_attempt_id BIGINT NOT NULL REFERENCES answer_attempts(id),
    reason_type VARCHAR(50) NOT NULL,
    priority INT NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_questions_category_id ON questions(category_id);
CREATE INDEX idx_questions_question_type ON questions(question_type);
CREATE INDEX idx_user_question_progress_user_status ON user_question_progress(user_id, current_status);
CREATE INDEX idx_user_question_progress_user_next_review ON user_question_progress(user_id, next_review_at);
CREATE INDEX idx_answer_attempts_user_question_submitted_desc ON answer_attempts(user_id, question_id, submitted_at DESC);
CREATE INDEX idx_review_queue_user_status_scheduled ON review_queue(user_id, status, scheduled_for);
CREATE INDEX idx_daily_cards_user_card_date ON daily_cards(user_id, card_date);
