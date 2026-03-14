package com.example.interviewplatform.interview.dto

import jakarta.validation.constraints.NotNull

data class SkipInterviewSessionQuestionRequest(
    @field:NotNull
    val sessionQuestionId: Long?,
)
