package com.example.interviewplatform.resume.dto

data class ResumeAchievementItemDto(
    val id: Long,
    val resumeExperienceSnapshotId: Long?,
    val resumeProjectSnapshotId: Long?,
    val title: String,
    val metricText: String?,
    val impactSummary: String,
    val sourceText: String?,
    val severityHint: String?,
    val displayOrder: Int,
)
