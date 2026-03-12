ALTER TABLE resume_versions
    ADD COLUMN llm_extraction_status VARCHAR(32),
    ADD COLUMN llm_extraction_started_at TIMESTAMPTZ,
    ADD COLUMN llm_extraction_completed_at TIMESTAMPTZ,
    ADD COLUMN llm_extraction_error_message TEXT,
    ADD COLUMN llm_model VARCHAR(128),
    ADD COLUMN llm_prompt_version VARCHAR(64),
    ADD COLUMN llm_extraction_confidence NUMERIC(5,4);

CREATE INDEX idx_resume_versions_resume_id_llm_extraction_status
    ON resume_versions (resume_id, llm_extraction_status);
