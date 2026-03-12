package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeProfileSnapshotResponseDto(
    val resumeVersionId: Long,
    val item: ResumeProfileSnapshotDto?,
    val generatedAt: Instant,
)
