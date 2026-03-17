package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_analyses")
class ResumeAnalysisEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "job_posting_id")
    val jobPostingId: Long? = null,
    @Column(name = "status", nullable = false)
    val status: String,
    @Column(name = "overall_score", nullable = false)
    val overallScore: Int,
    @Column(name = "match_summary", nullable = false)
    val matchSummary: String,
    @Column(name = "strong_matches_json", nullable = false)
    val strongMatchesJson: String,
    @Column(name = "missing_keywords_json", nullable = false)
    val missingKeywordsJson: String,
    @Column(name = "weak_signals_json", nullable = false)
    val weakSignalsJson: String,
    @Column(name = "recommended_focus_areas_json", nullable = false)
    val recommendedFocusAreasJson: String,
    @Column(name = "suggested_headline")
    val suggestedHeadline: String? = null,
    @Column(name = "suggested_summary")
    val suggestedSummary: String? = null,
    @Column(name = "recommended_format_type")
    val recommendedFormatType: String? = null,
    @Column(name = "generation_source", nullable = false)
    val generationSource: String,
    @Column(name = "llm_model")
    val llmModel: String? = null,
    @Column(name = "tailored_content_json")
    val tailoredContentJson: String? = null,
    @Column(name = "tailored_plain_text")
    val tailoredPlainText: String? = null,
    @Column(name = "section_order_json", nullable = false)
    val sectionOrderJson: String,
    @Column(name = "diff_summary")
    val diffSummary: String? = null,
    @Column(name = "analysis_notes_json", nullable = false)
    val analysisNotesJson: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
