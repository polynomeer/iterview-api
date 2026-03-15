package com.example.interviewplatform.interview.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateInterviewSessionRequest(
    @field:NotBlank
    val sessionType: String,
    @field:Min(1)
    @field:Max(10)
    val questionCount: Int = 5,
    val interviewMode: String? = null,
    val resumeVersionId: Long? = null,
    val sourceInterviewRecordId: Long? = null,
    val replayMode: String? = null,
    val seedQuestionIds: List<Long> = emptyList(),
)
