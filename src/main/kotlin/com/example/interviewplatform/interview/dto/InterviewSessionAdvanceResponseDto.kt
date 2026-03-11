package com.example.interviewplatform.interview.dto

data class InterviewSessionAdvanceResponseDto(
    val sessionId: Long,
    val status: String,
    val currentQuestion: InterviewSessionQuestionDto?,
    val summary: InterviewSessionSummaryDto,
)
