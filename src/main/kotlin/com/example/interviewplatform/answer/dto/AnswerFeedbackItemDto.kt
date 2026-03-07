package com.example.interviewplatform.answer.dto

import java.time.Instant

data class AnswerFeedbackItemDto(
    val id: Long,
    val feedbackType: String,
    val severity: String,
    val title: String,
    val body: String,
    val displayOrder: Int,
    val createdAt: Instant,
)
