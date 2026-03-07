package com.example.interviewplatform.answer.dto

import com.example.interviewplatform.question.dto.UserProgressSummaryDto

data class AnswerAttemptDetailResponseDto(
    val answerAttempt: AnswerAttemptDto,
    val score: ScoreSummaryDto,
    val feedback: List<AnswerFeedbackItemDto>,
    val progressSummary: UserProgressSummaryDto?,
)
