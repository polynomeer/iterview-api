package com.example.interviewplatform.answer.dto

import java.time.Instant

data class SubmitAnswerResponseDto(
    val answerAttemptId: Long,
    val scoreSummary: ScoreSummaryDto,
    val feedback: List<AnswerFeedbackItemDto>,
    val progressStatus: String,
    val nextReviewAt: Instant?,
    val archiveDecision: Boolean,
)
