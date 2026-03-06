package com.example.interviewplatform.question.dto

import java.math.BigDecimal

data class QuestionCompanyDto(
    val id: Long,
    val name: String,
    val relevanceScore: BigDecimal,
    val isPastFrequent: Boolean,
    val isTrendingRecent: Boolean,
)
