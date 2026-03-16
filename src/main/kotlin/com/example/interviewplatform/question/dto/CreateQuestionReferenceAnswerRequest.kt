package com.example.interviewplatform.question.dto

import jakarta.validation.constraints.NotBlank

data class CreateQuestionReferenceAnswerRequest(
    @field:NotBlank
    val title: String,
    @field:NotBlank
    val answerText: String,
    val answerFormat: String = "full_answer",
)
