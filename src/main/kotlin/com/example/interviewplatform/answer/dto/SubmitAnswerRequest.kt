package com.example.interviewplatform.answer.dto

import jakarta.validation.constraints.NotBlank

data class SubmitAnswerRequest(
    @field:NotBlank
    val contentText: String,
)
