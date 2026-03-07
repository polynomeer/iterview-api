package com.example.interviewplatform.review.dto

import java.time.Instant

data class ReviewQueueItemDto(
    val id: Long,
    val questionId: Long,
    val questionTitle: String,
    val questionDifficulty: String,
    val reasonType: String,
    val priority: Int,
    val scheduledFor: Instant,
    val status: String,
)
