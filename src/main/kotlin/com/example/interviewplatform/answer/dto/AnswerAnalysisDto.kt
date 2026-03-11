package com.example.interviewplatform.answer.dto

import java.math.BigDecimal
import java.time.Instant

data class AnswerAnalysisDto(
    val answerAttemptId: Long,
    val overallScore: BigDecimal,
    val depthScore: BigDecimal,
    val clarityScore: BigDecimal,
    val accuracyScore: BigDecimal,
    val exampleScore: BigDecimal,
    val tradeoffScore: BigDecimal,
    val confidenceScore: BigDecimal?,
    val strengthSummary: String,
    val weaknessSummary: String,
    val recommendedNextStep: String?,
    val createdAt: Instant,
)
