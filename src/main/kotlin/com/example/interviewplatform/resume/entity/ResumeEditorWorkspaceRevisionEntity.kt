package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_editor_workspace_revisions")
class ResumeEditorWorkspaceRevisionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_editor_workspace_id", nullable = false)
    val resumeEditorWorkspaceId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "revision_no", nullable = false)
    val revisionNo: Int,
    @Column(name = "change_source", nullable = false)
    val changeSource: String,
    @Column(name = "change_summary_json", nullable = false, columnDefinition = "TEXT")
    val changeSummaryJson: String,
    @Column(name = "markdown_source")
    val markdownSource: String? = null,
    @Column(name = "document_json", nullable = false, columnDefinition = "TEXT")
    val documentJson: String,
    @Column(name = "layout_metadata_json", columnDefinition = "TEXT")
    val layoutMetadataJson: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
