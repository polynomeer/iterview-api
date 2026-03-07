package com.example.interviewplatform.dailycard.dto

import java.time.LocalDate

data class HomeQuestionDto(
    val dailyCardId: Long,
    val questionId: Long,
    val title: String,
    val difficulty: String,
    val cardDate: LocalDate,
    val cardType: String,
    val status: String,
)
