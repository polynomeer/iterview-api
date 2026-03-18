package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_editor_comment_threads")
class ResumeEditorCommentThreadEntity(
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
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    val body: String,
    @Column(name = "status", nullable = false)
    val status: String,
    @Column(name = "resolved_at")
    val resolvedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
