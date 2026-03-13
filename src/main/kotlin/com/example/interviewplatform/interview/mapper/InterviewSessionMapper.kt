package com.example.interviewplatform.interview.mapper

import com.example.interviewplatform.interview.dto.InterviewSessionQuestionDto
import com.example.interviewplatform.interview.dto.InterviewSessionListItemDto
import com.example.interviewplatform.interview.dto.InterviewSessionSummaryDto
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.question.entity.QuestionEntity
import java.time.Instant

object InterviewSessionMapper {
    fun toQuestionDto(
        row: InterviewSessionQuestionEntity,
        question: QuestionEntity?,
        status: String,
    ): InterviewSessionQuestionDto = InterviewSessionQuestionDto(
        id = row.id,
        questionId = row.questionId,
        title = row.promptText ?: question?.title ?: "Interview Question",
        promptText = row.promptText,
        difficulty = question?.difficultyLevel ?: "UNKNOWN",
        orderIndex = row.orderIndex,
        status = status,
        sourceType = row.questionSourceType,
        parentSessionQuestionId = row.parentSessionQuestionId,
        isFollowUp = row.isFollowUp,
        depth = row.depth,
        categoryName = row.categoryName,
        answerAttemptId = row.answerAttemptId,
    )

    fun toSummaryDto(
        totalQuestions: Int,
        answeredQuestions: Int,
        averageScore: Double?,
    ): InterviewSessionSummaryDto = InterviewSessionSummaryDto(
        totalQuestions = totalQuestions,
        answeredQuestions = answeredQuestions,
        remainingQuestions = (totalQuestions - answeredQuestions).coerceAtLeast(0),
        averageScore = averageScore,
    )

    fun toListItemDto(
        id: Long,
        sessionType: String,
        status: String,
        resumeVersionId: Long?,
        startedAt: Instant,
        endedAt: Instant?,
        questionCount: Int,
        answeredCount: Int,
        averageScore: Double?,
    ): InterviewSessionListItemDto = InterviewSessionListItemDto(
        id = id,
        sessionType = sessionType,
        status = status,
        resumeVersionId = resumeVersionId,
        startedAt = startedAt,
        endedAt = endedAt,
        questionCount = questionCount,
        answeredCount = answeredCount,
        averageScore = averageScore,
    )
}
