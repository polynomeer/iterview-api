package com.example.interviewplatform.dailycard.dto

import java.time.Instant

data class OpenDailyCardResponseDto(
    val id: Long,
    val status: String,
    val openedAt: Instant,
)
