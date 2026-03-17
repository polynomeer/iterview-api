package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeAnalysisListItemDto(
    val id: Long,
    val resumeVersionId: Long,
    val jobPostingId: Long?,
    val status: String,
    val overallScore: Int,
    val matchSummary: String,
    val suggestedHeadline: String?,
    val recommendedFormatType: String?,
    val createdAt: Instant,
)
