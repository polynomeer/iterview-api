package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeCompetencyItemResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeCompetencyItemDto>,
    val generatedAt: Instant,
)
