package com.example.interviewplatform.review.dto

import java.time.Instant

data class ReviewDecision(
    val needsRetry: Boolean,
    val scheduledFor: Instant?,
)
