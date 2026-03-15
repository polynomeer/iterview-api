ALTER TABLE interview_records
    ADD COLUMN deterministic_summary TEXT,
    ADD COLUMN ai_enriched_summary TEXT,
    ADD COLUMN structuring_stage VARCHAR(32) NOT NULL DEFAULT 'deterministic';

UPDATE interview_records
SET deterministic_summary = overall_summary
WHERE deterministic_summary IS NULL;

ALTER TABLE interview_record_questions
    ADD COLUMN structuring_source VARCHAR(32) NOT NULL DEFAULT 'deterministic';

ALTER TABLE interview_record_answers
    ADD COLUMN structuring_source VARCHAR(32) NOT NULL DEFAULT 'deterministic';

ALTER TABLE interviewer_profiles
    ADD COLUMN structuring_source VARCHAR(32) NOT NULL DEFAULT 'deterministic';
