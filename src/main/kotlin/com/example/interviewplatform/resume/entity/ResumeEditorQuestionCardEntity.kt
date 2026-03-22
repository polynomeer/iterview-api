package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_editor_question_cards")
class ResumeEditorQuestionCardEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "resume_editor_workspace_id", nullable = false)
    val resumeEditorWorkspaceId: Long,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "block_id", nullable = false)
    val blockId: String,
    @Column(name = "field_path")
    val fieldPath: String? = null,
    @Column(name = "selection_start_offset")
    val selectionStartOffset: Int? = null,
    @Column(name = "selection_end_offset")
    val selectionEndOffset: Int? = null,
    @Column(name = "selected_text")
    val selectedText: String? = null,
    @Column(name = "anchor_path")
    val anchorPath: String? = null,
    @Column(name = "anchor_quote", columnDefinition = "TEXT")
    val anchorQuote: String? = null,
    @Column(name = "sentence_index")
    val sentenceIndex: Int? = null,
    @Column(name = "title")
    val title: String? = null,
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    val questionText: String,
    @Column(name = "question_type", nullable = false)
    val questionType: String,
    @Column(name = "source_type", nullable = false)
    val sourceType: String,
    @Column(name = "linked_question_id")
    val linkedQuestionId: Long? = null,
    @Column(name = "status", nullable = false)
    val status: String,
    @Column(name = "follow_up_suggestions_json", columnDefinition = "TEXT")
    val followUpSuggestionsJson: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
