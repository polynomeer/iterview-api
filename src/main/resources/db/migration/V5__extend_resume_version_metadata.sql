ALTER TABLE resume_versions
ADD COLUMN file_name VARCHAR(255),
ADD COLUMN file_type VARCHAR(100),
ADD COLUMN parsing_status VARCHAR(30) NOT NULL DEFAULT 'pending';

UPDATE resume_versions
SET parsing_status = CASE
    WHEN parsed_json IS NOT NULL OR raw_text IS NOT NULL OR summary_text IS NOT NULL THEN 'completed'
    ELSE 'pending'
END
WHERE parsing_status = 'pending';
