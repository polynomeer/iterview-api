package com.example.interviewplatform.review.dto

import java.math.BigDecimal
import java.time.Instant

data class ArchivedQuestionDto(
    val questionId: Long,
    val title: String,
    val difficulty: String,
    val archivedAt: Instant,
    val bestScore: BigDecimal?,
    val totalAttemptCount: Int,
)
