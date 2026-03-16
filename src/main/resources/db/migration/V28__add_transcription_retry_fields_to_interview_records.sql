ALTER TABLE interview_records
    ADD COLUMN transcript_error_code VARCHAR(100),
    ADD COLUMN transcript_error_message TEXT,
    ADD COLUMN transcript_retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN transcript_last_attempt_at TIMESTAMPTZ,
    ADD COLUMN transcript_processing_started_at TIMESTAMPTZ,
    ADD COLUMN transcript_next_retry_at TIMESTAMPTZ;

CREATE INDEX idx_interview_records_transcript_retry
    ON interview_records (transcript_status, transcript_next_retry_at);

CREATE INDEX idx_interview_records_transcript_processing_started
    ON interview_records (transcript_status, transcript_processing_started_at);
