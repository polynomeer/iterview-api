CREATE TABLE resume_document_overlay_targets (
    id BIGSERIAL PRIMARY KEY,
    resume_version_id BIGINT NOT NULL REFERENCES resume_versions (id) ON DELETE CASCADE,
    anchor_type VARCHAR(50) NOT NULL,
    anchor_record_id BIGINT,
    anchor_key VARCHAR(100),
    target_type VARCHAR(32) NOT NULL,
    field_path VARCHAR(120) NOT NULL,
    text_snippet TEXT NOT NULL,
    text_start_offset INTEGER,
    text_end_offset INTEGER,
    sentence_index INTEGER,
    paragraph_index INTEGER,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resume_document_overlay_targets_version_anchor
    ON resume_document_overlay_targets (resume_version_id, anchor_type, anchor_record_id, target_type);

CREATE INDEX idx_resume_document_overlay_targets_version_display
    ON resume_document_overlay_targets (resume_version_id, display_order, id);
