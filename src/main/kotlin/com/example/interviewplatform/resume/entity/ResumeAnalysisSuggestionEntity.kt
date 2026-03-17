package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_analysis_suggestions")
class ResumeAnalysisSuggestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_analysis_id", nullable = false)
    val resumeAnalysisId: Long,
    @Column(name = "section_key", nullable = false)
    val sectionKey: String,
    @Column(name = "original_text")
    val originalText: String? = null,
    @Column(name = "suggested_text", nullable = false)
    val suggestedText: String,
    @Column(name = "reason", nullable = false)
    val reason: String,
    @Column(name = "suggestion_type", nullable = false)
    val suggestionType: String,
    @Column(name = "accepted", nullable = false)
    val accepted: Boolean = false,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
