package com.example.interviewplatform.question.dto

import java.math.BigDecimal

data class QuestionRoleDto(
    val id: Long,
    val name: String,
    val relevanceScore: BigDecimal,
)
