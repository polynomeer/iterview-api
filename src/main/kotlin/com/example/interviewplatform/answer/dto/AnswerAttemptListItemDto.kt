package com.example.interviewplatform.answer.dto

import java.time.Instant

data class AnswerAttemptListItemDto(
    val id: Long,
    val attemptNo: Int,
    val answerMode: String,
    val submittedAt: Instant,
    val scoreSummary: ScoreSummaryDto,
)
