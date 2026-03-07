package com.example.interviewplatform.review.dto

import java.time.Instant

data class ReviewQueueActionResponseDto(
    val id: Long,
    val status: String,
    val updatedAt: Instant,
)
