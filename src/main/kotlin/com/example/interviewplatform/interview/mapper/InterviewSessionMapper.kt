package com.example.interviewplatform.interview.mapper

import com.example.interviewplatform.interview.dto.InterviewSessionQuestionDto
import com.example.interviewplatform.interview.dto.InterviewSessionSummaryDto
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.question.entity.QuestionEntity

object InterviewSessionMapper {
    fun toQuestionDto(
        row: InterviewSessionQuestionEntity,
        question: QuestionEntity,
        status: String,
    ): InterviewSessionQuestionDto = InterviewSessionQuestionDto(
        id = row.id,
        questionId = row.questionId,
        title = question.title,
        difficulty = question.difficultyLevel,
        orderIndex = row.orderIndex,
        status = status,
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
}
