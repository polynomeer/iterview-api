ALTER TABLE resume_question_heatmap_links
    ADD COLUMN overlay_target_type VARCHAR(32),
    ADD COLUMN overlay_field_path VARCHAR(120),
    ADD COLUMN overlay_sentence_index INTEGER,
    ADD COLUMN overlay_text_snippet VARCHAR(500);

CREATE INDEX idx_resume_question_heatmap_links_overlay
    ON resume_question_heatmap_links (resume_version_id, overlay_target_type, overlay_field_path, overlay_sentence_index);
