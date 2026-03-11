package com.example.interviewplatform.skill.dto

import java.math.BigDecimal

data class SkillRadarCategoryDto(
    val categoryCode: String,
    val label: String,
    val score: BigDecimal,
    val benchmarkScore: BigDecimal?,
    val gapScore: BigDecimal?,
)
