package com.example.interviewplatform.interview.dto

import java.time.Instant

data class InterviewSessionDetailResponseDto(
    val id: Long,
    val sessionType: String,
    val status: String,
    val resumeVersionId: Long?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val currentQuestion: InterviewSessionQuestionDto?,
    val questions: List<InterviewSessionQuestionDto>,
    val summary: InterviewSessionSummaryDto,
)
