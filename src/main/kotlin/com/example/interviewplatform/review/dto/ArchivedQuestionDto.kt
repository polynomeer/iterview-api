package com.example.interviewplatform.review.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "Archived question summary for mastered questions")
data class ArchivedQuestionDto(
    @field:Schema(description = "Archived question id")
    val questionId: Long,
    @field:Schema(description = "Question title")
    val title: String,
    @field:Schema(description = "Question difficulty level")
    val difficulty: String,
    @field:Schema(description = "Timestamp when the question was archived")
    val archivedAt: Instant,
    @field:Schema(description = "Best score achieved for the question", nullable = true)
    val bestScore: BigDecimal?,
    @field:Schema(description = "Number of attempts submitted before archiving")
    val totalAttemptCount: Int,
    @field:Schema(description = "Archive source type", nullable = true, example = "practice")
    val sourceType: String?,
    @field:Schema(description = "Archive source label", nullable = true, example = "Practice")
    val sourceLabel: String?,
    @field:Schema(description = "Interview session id when archived from interview", nullable = true)
    val sourceSessionId: Long?,
    @field:Schema(description = "Interview session question id when archived from interview", nullable = true)
    val sourceSessionQuestionId: Long?,
    @field:Schema(description = "Imported interview record id when archived from a real interview asset", nullable = true)
    val sourceInterviewRecordId: Long? = null,
    @field:Schema(description = "Imported interview question id when archived from a real interview asset", nullable = true)
    val sourceInterviewQuestionId: Long? = null,
    @field:Schema(description = "Whether the archived question was a follow-up")
    val isFollowUp: Boolean,
)
