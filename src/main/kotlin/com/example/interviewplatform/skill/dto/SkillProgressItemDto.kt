package com.example.interviewplatform.skill.dto

import java.math.BigDecimal
import java.time.Instant

data class SkillProgressItemDto(
    val categoryCode: String,
    val label: String,
    val score: BigDecimal,
    val benchmarkScore: BigDecimal?,
    val gapScore: BigDecimal?,
    val answeredQuestionCount: Int,
    val weakQuestionCount: Int,
    val calculatedAt: Instant,
)
