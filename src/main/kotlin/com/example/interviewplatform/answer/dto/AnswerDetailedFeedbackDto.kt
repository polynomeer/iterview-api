package com.example.interviewplatform.answer.dto

data class AnswerDetailedFeedbackDto(
    val contentLocale: String?,
    val llmModel: String?,
    val detailedFeedback: String?,
    val strengthPoints: List<String>,
    val improvementPoints: List<String>,
    val missedPoints: List<String>,
)
