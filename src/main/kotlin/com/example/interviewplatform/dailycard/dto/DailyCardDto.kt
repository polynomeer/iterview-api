package com.example.interviewplatform.dailycard.dto

import java.time.LocalDate

data class DailyCardDto(
    val id: Long,
    val questionId: Long,
    val cardDate: LocalDate,
    val status: String,
)
