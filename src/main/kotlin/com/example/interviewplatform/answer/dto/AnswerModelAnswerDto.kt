package com.example.interviewplatform.answer.dto

data class AnswerModelAnswerDto(
    val sourceType: String,
    val contentLocale: String?,
    val llmModel: String?,
    val text: String,
)
