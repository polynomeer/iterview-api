package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeExperienceSnapshotResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeExperienceSnapshotDto>,
    val generatedAt: Instant,
)
