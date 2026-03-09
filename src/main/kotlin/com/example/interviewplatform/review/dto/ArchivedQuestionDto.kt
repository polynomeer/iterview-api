package com.example.interviewplatform.review.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "Archived question summary for mastered questions")
data class ArchivedQuestionDto(
    @field:Schema(description = "Archived question id")
    val questionId: Long,
    @field:Schema(description = "Question title")
    val title: String,
    @field:Schema(description = "Question difficulty level")
    val difficulty: String,
    @field:Schema(description = "Timestamp when the question was archived")
    val archivedAt: Instant,
    @field:Schema(description = "Best score achieved for the question", nullable = true)
    val bestScore: BigDecimal?,
    @field:Schema(description = "Number of attempts submitted before archiving")
    val totalAttemptCount: Int,
)
