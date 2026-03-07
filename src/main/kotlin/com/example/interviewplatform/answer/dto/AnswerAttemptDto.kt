package com.example.interviewplatform.answer.dto

import java.time.Instant

data class AnswerAttemptDto(
    val id: Long,
    val questionId: Long,
    val resumeVersionId: Long?,
    val sourceDailyCardId: Long?,
    val attemptNo: Int,
    val answerMode: String,
    val contentText: String,
    val submittedAt: Instant,
    val createdAt: Instant,
)
