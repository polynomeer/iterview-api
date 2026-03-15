CREATE TABLE interview_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    company_name VARCHAR(255),
    role_name VARCHAR(255),
    interview_date DATE,
    interview_type VARCHAR(64) NOT NULL,
    source_audio_file_url TEXT,
    source_audio_file_name VARCHAR(255),
    source_audio_duration_ms BIGINT,
    source_audio_content_type VARCHAR(128),
    raw_transcript TEXT,
    cleaned_transcript TEXT,
    confirmed_transcript TEXT,
    transcript_status VARCHAR(32) NOT NULL,
    analysis_status VARCHAR(32) NOT NULL,
    linked_resume_version_id BIGINT REFERENCES resume_versions(id),
    linked_job_posting_id BIGINT,
    interviewer_profile_id BIGINT,
    overall_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_interview_records_user_created_at
    ON interview_records (user_id, created_at DESC);

CREATE TABLE interview_transcript_segments (
    id BIGSERIAL PRIMARY KEY,
    interview_record_id BIGINT NOT NULL REFERENCES interview_records(id) ON DELETE CASCADE,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    speaker_type VARCHAR(32) NOT NULL,
    raw_text TEXT,
    cleaned_text TEXT,
    confirmed_text TEXT,
    confidence_score NUMERIC(5,2),
    sequence INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_interview_transcript_segments_record_sequence
    ON interview_transcript_segments (interview_record_id, sequence);

CREATE TABLE interview_record_questions (
    id BIGSERIAL PRIMARY KEY,
    interview_record_id BIGINT NOT NULL REFERENCES interview_records(id) ON DELETE CASCADE,
    segment_start_id BIGINT REFERENCES interview_transcript_segments(id) ON DELETE SET NULL,
    segment_end_id BIGINT REFERENCES interview_transcript_segments(id) ON DELETE SET NULL,
    text TEXT NOT NULL,
    normalized_text TEXT,
    question_type VARCHAR(64) NOT NULL,
    topic_tags_json TEXT NOT NULL,
    intent_tags_json TEXT NOT NULL,
    derived_from_resume_section VARCHAR(64),
    derived_from_resume_record_type VARCHAR(64),
    derived_from_resume_record_id BIGINT,
    derived_from_job_posting_section VARCHAR(64),
    parent_question_id BIGINT REFERENCES interview_record_questions(id) ON DELETE SET NULL,
    order_index INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_interview_record_questions_record_order
    ON interview_record_questions (interview_record_id, order_index);

CREATE TABLE interview_record_answers (
    id BIGSERIAL PRIMARY KEY,
    interview_record_question_id BIGINT NOT NULL REFERENCES interview_record_questions(id) ON DELETE CASCADE,
    segment_start_id BIGINT REFERENCES interview_transcript_segments(id) ON DELETE SET NULL,
    segment_end_id BIGINT REFERENCES interview_transcript_segments(id) ON DELETE SET NULL,
    text TEXT NOT NULL,
    normalized_text TEXT,
    summary TEXT,
    confidence_markers_json TEXT NOT NULL,
    weakness_tags_json TEXT NOT NULL,
    strength_tags_json TEXT NOT NULL,
    analysis_json TEXT,
    order_index INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_interview_record_answers_question
    ON interview_record_answers (interview_record_question_id);

CREATE TABLE interview_record_follow_up_edges (
    id BIGSERIAL PRIMARY KEY,
    interview_record_id BIGINT NOT NULL REFERENCES interview_records(id) ON DELETE CASCADE,
    from_question_id BIGINT NOT NULL REFERENCES interview_record_questions(id) ON DELETE CASCADE,
    to_question_id BIGINT NOT NULL REFERENCES interview_record_questions(id) ON DELETE CASCADE,
    relation_type VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_interview_record_follow_up_edges_record
    ON interview_record_follow_up_edges (interview_record_id, from_question_id);

CREATE TABLE interviewer_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    source_interview_record_id BIGINT NOT NULL REFERENCES interview_records(id) ON DELETE CASCADE,
    style_tags_json TEXT NOT NULL,
    tone_profile VARCHAR(64) NOT NULL,
    pressure_level VARCHAR(32) NOT NULL,
    depth_preference VARCHAR(32) NOT NULL,
    follow_up_pattern_json TEXT NOT NULL,
    favorite_topics_json TEXT NOT NULL,
    opening_pattern VARCHAR(64),
    closing_pattern VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_interviewer_profiles_source_record
    ON interviewer_profiles (source_interview_record_id);

ALTER TABLE interview_records
    ADD CONSTRAINT fk_interview_records_interviewer_profile
    FOREIGN KEY (interviewer_profile_id) REFERENCES interviewer_profiles(id) ON DELETE SET NULL;
