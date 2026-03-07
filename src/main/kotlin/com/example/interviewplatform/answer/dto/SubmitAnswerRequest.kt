package com.example.interviewplatform.answer.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SubmitAnswerRequest(
    val resumeVersionId: Long? = null,
    @field:NotBlank
    val answerMode: String = "text",
    @field:NotBlank
    @field:Size(max = 20000)
    val contentText: String,
)
