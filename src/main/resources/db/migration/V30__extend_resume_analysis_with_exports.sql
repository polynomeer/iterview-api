ALTER TABLE job_postings
    ADD COLUMN fetch_status VARCHAR(32) NOT NULL DEFAULT 'provided',
    ADD COLUMN fetched_title VARCHAR(255),
    ADD COLUMN fetch_error_message TEXT,
    ADD COLUMN fetched_at TIMESTAMPTZ;

ALTER TABLE resume_analyses
    ADD COLUMN generation_source VARCHAR(32) NOT NULL DEFAULT 'deterministic',
    ADD COLUMN llm_model VARCHAR(100),
    ADD COLUMN tailored_content_json TEXT,
    ADD COLUMN tailored_plain_text TEXT,
    ADD COLUMN section_order_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN diff_summary TEXT,
    ADD COLUMN analysis_notes_json TEXT NOT NULL DEFAULT '[]';

CREATE TABLE resume_analysis_exports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_analysis_id BIGINT NOT NULL REFERENCES resume_analyses (id) ON DELETE CASCADE,
    export_type VARCHAR(32) NOT NULL,
    format_type VARCHAR(64),
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    page_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resume_analysis_exports_analysis_created_at
    ON resume_analysis_exports (resume_analysis_id, created_at DESC);

CREATE INDEX idx_resume_analysis_exports_user_created_at
    ON resume_analysis_exports (user_id, created_at DESC);
