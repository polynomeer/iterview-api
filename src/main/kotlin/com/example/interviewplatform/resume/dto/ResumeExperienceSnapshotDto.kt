package com.example.interviewplatform.resume.dto

import java.time.LocalDate

data class ResumeExperienceSnapshotDto(
    val id: Long,
    val companyName: String?,
    val roleName: String?,
    val employmentType: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val current: Boolean,
    val projectName: String?,
    val summaryText: String,
    val impactText: String?,
    val sourceText: String,
    val riskLevel: String,
    val displayOrder: Int,
    val confirmed: Boolean,
)
