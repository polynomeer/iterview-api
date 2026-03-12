package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeEducationItemResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeEducationItemDto>,
    val generatedAt: Instant,
)
