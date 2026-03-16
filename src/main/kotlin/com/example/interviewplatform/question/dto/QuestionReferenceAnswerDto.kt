package com.example.interviewplatform.question.dto

data class QuestionReferenceAnswerDto(
    val id: Long,
    val title: String,
    val answerText: String,
    val answerFormat: String,
    val sourceType: String,
    val sourceLabel: String,
    val contentLocale: String?,
    val isUserGenerated: Boolean,
    val targetRoleId: Long?,
    val companyId: Long?,
    val isOfficial: Boolean,
    val displayOrder: Int,
)
