package com.example.interviewplatform.resume.dto

import java.time.LocalDate

data class ResumeEducationItemDto(
    val id: Long,
    val institutionName: String,
    val degreeName: String?,
    val fieldOfStudy: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val description: String?,
    val displayOrder: Int,
    val sourceText: String?,
)
