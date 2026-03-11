package com.example.interviewplatform.dailycard.dto

import java.math.BigDecimal

data class HomeSkillRadarPreviewDto(
    val categoryCode: String,
    val score: BigDecimal,
    val gapScore: BigDecimal?,
)
