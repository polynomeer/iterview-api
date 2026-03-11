package com.example.interviewplatform.resume.dto

data class ResumeRiskItemDto(
    val id: Long,
    val linkedQuestionId: Long?,
    val riskType: String,
    val title: String,
    val description: String,
    val severity: String,
)
