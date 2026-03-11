package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeRiskItemResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeRiskItemDto>,
    val generatedAt: Instant,
)
