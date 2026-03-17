package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "resume_question_heatmap_links")
class ResumeQuestionHeatmapLinkEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "interview_record_question_id", nullable = false)
    val interviewRecordQuestionId: Long,
    @Column(name = "anchor_type", nullable = false)
    val anchorType: String,
    @Column(name = "anchor_record_id")
    val anchorRecordId: Long? = null,
    @Column(name = "anchor_key")
    val anchorKey: String? = null,
    @Column(name = "overlay_target_type")
    val overlayTargetType: String? = null,
    @Column(name = "overlay_field_path")
    val overlayFieldPath: String? = null,
    @Column(name = "overlay_sentence_index")
    val overlaySentenceIndex: Int? = null,
    @Column(name = "overlay_text_snippet")
    val overlayTextSnippet: String? = null,
    @Column(name = "link_source", nullable = false)
    val linkSource: String,
    @Column(name = "confidence_score", precision = 5, scale = 4)
    val confidenceScore: BigDecimal? = null,
    @Column(name = "active", nullable = false)
    val active: Boolean = true,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
