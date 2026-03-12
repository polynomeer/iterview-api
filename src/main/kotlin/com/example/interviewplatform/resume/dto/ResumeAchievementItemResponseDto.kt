package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeAchievementItemResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeAchievementItemDto>,
    val generatedAt: Instant,
)
