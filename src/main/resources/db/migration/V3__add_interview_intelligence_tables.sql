CREATE TABLE skill_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE skills (
    id BIGSERIAL PRIMARY KEY,
    skill_category_id BIGINT NOT NULL REFERENCES skill_categories(id),
    name VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE resume_skill_snapshots (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id),
    skill_id BIGINT REFERENCES skills(id),
    skill_name VARCHAR(120) NOT NULL,
    source_text TEXT,
    confidence_score NUMERIC(5,2),
    is_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE resume_experience_snapshots (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id),
    project_name VARCHAR(255),
    summary_text TEXT NOT NULL,
    impact_text TEXT,
    source_text TEXT NOT NULL,
    risk_level VARCHAR(30) NOT NULL,
    display_order INT NOT NULL,
    is_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE resume_risk_items (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id),
    resume_experience_snapshot_id BIGINT REFERENCES resume_experience_snapshots(id),
    linked_question_id BIGINT REFERENCES questions(id),
    risk_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE question_relationships (
    id BIGSERIAL PRIMARY KEY,
    parent_question_id BIGINT NOT NULL REFERENCES questions(id),
    child_question_id BIGINT NOT NULL REFERENCES questions(id),
    relationship_type VARCHAR(50) NOT NULL,
    depth INT NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (parent_question_id, child_question_id, relationship_type)
);

CREATE TABLE question_skill_mappings (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    skill_id BIGINT NOT NULL REFERENCES skills(id),
    weight NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (question_id, skill_id)
);

CREATE TABLE answer_analyses (
    id BIGSERIAL PRIMARY KEY,
    answer_attempt_id BIGINT NOT NULL UNIQUE REFERENCES answer_attempts(id),
    overall_score NUMERIC(5,2) NOT NULL,
    depth_score NUMERIC(5,2) NOT NULL,
    clarity_score NUMERIC(5,2) NOT NULL,
    accuracy_score NUMERIC(5,2) NOT NULL,
    example_score NUMERIC(5,2) NOT NULL,
    tradeoff_score NUMERIC(5,2) NOT NULL,
    confidence_score NUMERIC(5,2),
    strength_summary TEXT NOT NULL,
    weakness_summary TEXT NOT NULL,
    recommended_next_step TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE skill_category_scores (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    skill_category_id BIGINT NOT NULL REFERENCES skill_categories(id),
    score NUMERIC(5,2) NOT NULL,
    answered_question_count INT NOT NULL DEFAULT 0,
    weak_question_count INT NOT NULL DEFAULT 0,
    benchmark_score NUMERIC(5,2),
    gap_score NUMERIC(5,2),
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, skill_category_id)
);

CREATE TABLE career_benchmarks (
    id BIGSERIAL PRIMARY KEY,
    job_role_id BIGINT NOT NULL REFERENCES job_roles(id),
    experience_band_code VARCHAR(50) NOT NULL,
    skill_category_id BIGINT NOT NULL REFERENCES skill_categories(id),
    benchmark_score NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (job_role_id, experience_band_code, skill_category_id)
);

CREATE TABLE interview_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    resume_version_id BIGINT REFERENCES resume_versions(id),
    session_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE interview_session_questions (
    id BIGSERIAL PRIMARY KEY,
    interview_session_id BIGINT NOT NULL REFERENCES interview_sessions(id),
    question_id BIGINT NOT NULL REFERENCES questions(id),
    order_index INT NOT NULL,
    answer_attempt_id BIGINT REFERENCES answer_attempts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (interview_session_id, order_index)
);

CREATE INDEX idx_skill_categories_display_order ON skill_categories(display_order);
CREATE INDEX idx_skills_skill_category_id ON skills(skill_category_id);
CREATE INDEX idx_resume_skill_snapshots_resume_version_category ON resume_skill_snapshots(resume_version_id, skill_id);
CREATE INDEX idx_resume_experience_snapshots_resume_version_risk ON resume_experience_snapshots(resume_version_id, risk_level);
CREATE INDEX idx_resume_risk_items_resume_version_severity ON resume_risk_items(resume_version_id, severity);
CREATE INDEX idx_question_relationships_parent_order ON question_relationships(parent_question_id, display_order);
CREATE INDEX idx_question_relationships_child ON question_relationships(child_question_id);
CREATE INDEX idx_question_skill_mappings_question_id ON question_skill_mappings(question_id);
CREATE INDEX idx_question_skill_mappings_skill_id ON question_skill_mappings(skill_id);
CREATE INDEX idx_skill_category_scores_user_category ON skill_category_scores(user_id, skill_category_id);
CREATE INDEX idx_career_benchmarks_role_experience_category ON career_benchmarks(job_role_id, experience_band_code, skill_category_id);
CREATE INDEX idx_interview_sessions_user_status_started ON interview_sessions(user_id, status, started_at DESC);
CREATE INDEX idx_interview_session_questions_session_order ON interview_session_questions(interview_session_id, order_index);
