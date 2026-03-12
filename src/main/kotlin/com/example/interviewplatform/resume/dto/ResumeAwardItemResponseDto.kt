package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeAwardItemResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeAwardItemDto>,
    val generatedAt: Instant,
)
