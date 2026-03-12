package com.example.interviewplatform.resume.dto

data class ResumeCompetencyItemDto(
    val id: Long,
    val title: String,
    val description: String,
    val sourceText: String?,
    val displayOrder: Int,
)
