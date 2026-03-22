ALTER TABLE resume_editor_comment_threads
    ADD COLUMN anchor_path VARCHAR(512),
    ADD COLUMN anchor_quote TEXT,
    ADD COLUMN sentence_index INTEGER;

ALTER TABLE resume_editor_question_cards
    ADD COLUMN anchor_path VARCHAR(512),
    ADD COLUMN anchor_quote TEXT,
    ADD COLUMN sentence_index INTEGER;

CREATE INDEX idx_resume_editor_comment_threads_sentence_index
    ON resume_editor_comment_threads (resume_editor_workspace_id, sentence_index);

CREATE INDEX idx_resume_editor_question_cards_sentence_index
    ON resume_editor_question_cards (resume_editor_workspace_id, sentence_index);
