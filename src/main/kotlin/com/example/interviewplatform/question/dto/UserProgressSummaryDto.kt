package com.example.interviewplatform.question.dto

import java.math.BigDecimal
import java.time.Instant

data class UserProgressSummaryDto(
    val currentStatus: String,
    val latestScore: BigDecimal?,
    val bestScore: BigDecimal?,
    val totalAttemptCount: Int,
    val lastAnsweredAt: Instant?,
    val nextReviewAt: Instant?,
    val masteryLevel: String?,
)
