package com.example.interviewplatform.resume.dto

data class ResumeExperienceSnapshotDto(
    val id: Long,
    val projectName: String?,
    val summaryText: String,
    val impactText: String?,
    val sourceText: String,
    val riskLevel: String,
    val displayOrder: Int,
    val confirmed: Boolean,
)
