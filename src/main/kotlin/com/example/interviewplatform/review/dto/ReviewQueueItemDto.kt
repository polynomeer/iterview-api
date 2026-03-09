package com.example.interviewplatform.review.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Pending review queue item for the current user")
data class ReviewQueueItemDto(
    @field:Schema(description = "Review queue row id")
    val id: Long,
    @field:Schema(description = "Question id associated with this retry item")
    val questionId: Long,
    @field:Schema(description = "Question title shown in the retry queue")
    val questionTitle: String,
    @field:Schema(description = "Question difficulty level")
    val questionDifficulty: String,
    @field:Schema(description = "Reason that triggered the retry item", example = "low_total")
    val reasonType: String,
    @field:Schema(description = "Priority score used for ordering")
    val priority: Int,
    @field:Schema(description = "When the retry becomes eligible")
    val scheduledFor: Instant,
    @field:Schema(description = "Current queue status", example = "pending")
    val status: String,
)
