package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeProjectSnapshotResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeProjectSnapshotDto>,
    val generatedAt: Instant,
)
