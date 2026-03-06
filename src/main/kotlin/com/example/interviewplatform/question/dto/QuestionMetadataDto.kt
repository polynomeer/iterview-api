package com.example.interviewplatform.question.dto

import java.time.Instant

data class QuestionMetadataDto(
    val id: Long,
    val title: String,
    val body: String,
    val questionType: String,
    val difficulty: String,
    val sourceType: String,
    val qualityStatus: String,
    val visibility: String,
    val categoryId: Long,
    val categoryName: String?,
    val expectedAnswerSeconds: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
