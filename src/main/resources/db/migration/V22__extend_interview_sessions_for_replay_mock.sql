ALTER TABLE interview_sessions
    ADD COLUMN source_interview_record_id BIGINT REFERENCES interview_records(id),
    ADD COLUMN replay_mode VARCHAR(30);

CREATE INDEX idx_interview_sessions_user_source_record_started
    ON interview_sessions (user_id, source_interview_record_id, started_at DESC);
