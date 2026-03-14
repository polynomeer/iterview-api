ALTER TABLE interview_session_evidence_items
    ADD COLUMN facet VARCHAR(32) NOT NULL DEFAULT 'general';

CREATE INDEX idx_interview_session_evidence_items_session_facet
    ON interview_session_evidence_items (interview_session_id, facet);
