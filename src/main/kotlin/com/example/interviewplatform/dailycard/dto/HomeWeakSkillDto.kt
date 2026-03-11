package com.example.interviewplatform.dailycard.dto

import java.math.BigDecimal

data class HomeWeakSkillDto(
    val categoryCode: String,
    val label: String,
    val gapScore: BigDecimal?,
)
