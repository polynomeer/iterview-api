package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_document_overlay_targets")
class ResumeDocumentOverlayTargetEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "anchor_type", nullable = false)
    val anchorType: String,
    @Column(name = "anchor_record_id")
    val anchorRecordId: Long? = null,
    @Column(name = "anchor_key")
    val anchorKey: String? = null,
    @Column(name = "target_type", nullable = false)
    val targetType: String,
    @Column(name = "field_path", nullable = false)
    val fieldPath: String,
    @Column(name = "text_snippet", nullable = false)
    val textSnippet: String,
    @Column(name = "text_start_offset")
    val textStartOffset: Int? = null,
    @Column(name = "text_end_offset")
    val textEndOffset: Int? = null,
    @Column(name = "sentence_index")
    val sentenceIndex: Int? = null,
    @Column(name = "paragraph_index")
    val paragraphIndex: Int? = null,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
