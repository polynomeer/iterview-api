package com.example.interviewplatform.answer.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Answer submission result including scoring, feedback, and progress updates")
data class SubmitAnswerResponseDto(
    @field:Schema(description = "Created answer attempt id")
    val answerAttemptId: Long,
    @field:Schema(description = "Scoring summary for the attempt")
    val scoreSummary: ScoreSummaryDto,
    @field:Schema(description = "Feedback items generated for the attempt")
    val feedback: List<AnswerFeedbackItemDto>,
    @field:Schema(description = "Updated progress status for the user-question pair", example = "retry_pending")
    val progressStatus: String,
    @field:Schema(description = "Scheduled retry timestamp when a retry is required", nullable = true)
    val nextReviewAt: Instant?,
    @field:Schema(description = "Whether the question was archived as mastered")
    val archiveDecision: Boolean,
)
