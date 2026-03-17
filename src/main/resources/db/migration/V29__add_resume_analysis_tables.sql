CREATE TABLE job_postings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    input_type VARCHAR(30) NOT NULL,
    source_url TEXT,
    raw_text TEXT,
    company_name VARCHAR(255),
    role_name VARCHAR(255),
    parsed_requirements_json TEXT NOT NULL,
    parsed_nice_to_have_json TEXT NOT NULL,
    parsed_keywords_json TEXT NOT NULL,
    parsed_responsibilities_json TEXT NOT NULL,
    parsed_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_job_postings_user_created_at
    ON job_postings (user_id, created_at DESC, id DESC);

CREATE TABLE resume_analyses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions(id),
    job_posting_id BIGINT REFERENCES job_postings(id),
    status VARCHAR(30) NOT NULL,
    overall_score INT NOT NULL,
    match_summary TEXT NOT NULL,
    strong_matches_json TEXT NOT NULL,
    missing_keywords_json TEXT NOT NULL,
    weak_signals_json TEXT NOT NULL,
    recommended_focus_areas_json TEXT NOT NULL,
    suggested_headline TEXT,
    suggested_summary TEXT,
    recommended_format_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_analyses_version_created_at
    ON resume_analyses (resume_version_id, created_at DESC, id DESC);

CREATE INDEX idx_resume_analyses_job_posting
    ON resume_analyses (job_posting_id, created_at DESC, id DESC);

CREATE TABLE resume_analysis_suggestions (
    id BIGSERIAL PRIMARY KEY,
    resume_analysis_id BIGINT NOT NULL REFERENCES resume_analyses(id) ON DELETE CASCADE,
    section_key VARCHAR(100) NOT NULL,
    original_text TEXT,
    suggested_text TEXT NOT NULL,
    reason TEXT NOT NULL,
    suggestion_type VARCHAR(50) NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT false,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resume_analysis_suggestions_analysis_display
    ON resume_analysis_suggestions (resume_analysis_id, display_order, id);
