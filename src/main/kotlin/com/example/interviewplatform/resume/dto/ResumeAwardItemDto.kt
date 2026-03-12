package com.example.interviewplatform.resume.dto

import java.time.LocalDate

data class ResumeAwardItemDto(
    val id: Long,
    val title: String,
    val issuerName: String?,
    val awardedOn: LocalDate?,
    val description: String?,
    val displayOrder: Int,
    val sourceText: String?,
)
