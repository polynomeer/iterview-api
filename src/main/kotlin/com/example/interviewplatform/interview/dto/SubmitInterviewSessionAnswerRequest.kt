package com.example.interviewplatform.interview.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class SubmitInterviewSessionAnswerRequest(
    @field:NotNull
    val sessionQuestionId: Long?,
    @field:NotBlank
    val answerMode: String,
    @field:NotBlank
    val contentText: String,
    val resumeVersionId: Long? = null,
)
