package com.example.interviewplatform.resume.dto

import java.time.LocalDate

data class ResumeProjectSnapshotDto(
    val id: Long,
    val resumeExperienceSnapshotId: Long?,
    val title: String,
    val organizationName: String?,
    val roleName: String?,
    val summaryText: String,
    val techStackText: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val displayOrder: Int,
    val sourceText: String?,
)
