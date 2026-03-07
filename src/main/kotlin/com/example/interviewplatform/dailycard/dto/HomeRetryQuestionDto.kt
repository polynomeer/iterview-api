package com.example.interviewplatform.dailycard.dto

import java.time.Instant

data class HomeRetryQuestionDto(
    val reviewQueueId: Long,
    val questionId: Long,
    val title: String,
    val difficulty: String,
    val priority: Int,
    val scheduledFor: Instant,
)
