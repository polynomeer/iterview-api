package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeSkillSnapshotResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeSkillSnapshotDto>,
    val generatedAt: Instant,
)
