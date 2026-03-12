ALTER TABLE resume_versions
ADD COLUMN storage_key VARCHAR(500),
ADD COLUMN file_size_bytes BIGINT,
ADD COLUMN checksum_sha256 VARCHAR(64),
ADD COLUMN parse_started_at TIMESTAMPTZ,
ADD COLUMN parse_completed_at TIMESTAMPTZ,
ADD COLUMN parse_error_message TEXT;

CREATE INDEX idx_resume_versions_resume_id_uploaded_at
    ON resume_versions (resume_id, uploaded_at DESC);

CREATE INDEX idx_resume_versions_parsing_status
    ON resume_versions (parsing_status);
