package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeAnalysisDto(
    val id: Long,
    val resumeVersionId: Long,
    val jobPostingId: Long?,
    val status: String,
    val overallScore: Int,
    val matchSummary: String,
    val strongMatches: List<String>,
    val missingKeywords: List<String>,
    val weakSignals: List<String>,
    val recommendedFocusAreas: List<String>,
    val suggestedHeadline: String?,
    val suggestedSummary: String?,
    val recommendedFormatType: String?,
    val suggestions: List<ResumeAnalysisSuggestionDto>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
