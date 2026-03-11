package com.example.interviewplatform.answer.mapper

import com.example.interviewplatform.answer.dto.AnswerAttemptDetailResponseDto
import com.example.interviewplatform.answer.dto.AnswerAttemptDto
import com.example.interviewplatform.answer.dto.AnswerAnalysisDto
import com.example.interviewplatform.answer.dto.AnswerAttemptListItemDto
import com.example.interviewplatform.answer.dto.AnswerFeedbackItemDto
import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import com.example.interviewplatform.answer.dto.SubmitAnswerResponseDto
import com.example.interviewplatform.answer.entity.AnswerAnalysisEntity
import com.example.interviewplatform.answer.entity.AnswerAttemptEntity
import com.example.interviewplatform.answer.entity.AnswerFeedbackItemEntity
import com.example.interviewplatform.answer.entity.AnswerScoreEntity
import com.example.interviewplatform.question.dto.UserProgressSummaryDto

object AnswerMapper {
    fun toSubmitResponse(
        answerAttemptId: Long,
        scoreSummary: ScoreSummaryDto,
        feedback: List<AnswerFeedbackItemDto>,
        progressStatus: String,
        nextReviewAt: java.time.Instant?,
        archiveDecision: Boolean,
    ): SubmitAnswerResponseDto = SubmitAnswerResponseDto(
        answerAttemptId = answerAttemptId,
        scoreSummary = scoreSummary,
        feedback = feedback,
        progressStatus = progressStatus,
        nextReviewAt = nextReviewAt,
        archiveDecision = archiveDecision,
    )

    fun toListItem(
        attempt: AnswerAttemptEntity,
        score: ScoreSummaryDto,
    ): AnswerAttemptListItemDto = AnswerAttemptListItemDto(
        id = attempt.id,
        attemptNo = attempt.attemptNo,
        answerMode = attempt.answerMode,
        submittedAt = attempt.submittedAt,
        scoreSummary = score,
    )

    fun toAttemptDto(attempt: AnswerAttemptEntity): AnswerAttemptDto = AnswerAttemptDto(
        id = attempt.id,
        questionId = attempt.questionId,
        resumeVersionId = attempt.resumeVersionId,
        sourceDailyCardId = attempt.sourceDailyCardId,
        attemptNo = attempt.attemptNo,
        answerMode = attempt.answerMode,
        contentText = attempt.contentText,
        submittedAt = attempt.submittedAt,
        createdAt = attempt.createdAt,
    )

    fun toDetailResponse(
        attempt: AnswerAttemptEntity,
        score: ScoreSummaryDto,
        feedback: List<AnswerFeedbackItemDto>,
        progressSummary: UserProgressSummaryDto?,
    ): AnswerAttemptDetailResponseDto = AnswerAttemptDetailResponseDto(
        answerAttempt = toAttemptDto(attempt),
        score = score,
        feedback = feedback,
        progressSummary = progressSummary,
    )

    fun toFeedbackDto(entity: AnswerFeedbackItemEntity): AnswerFeedbackItemDto = AnswerFeedbackItemDto(
        id = entity.id,
        feedbackType = entity.feedbackType,
        severity = entity.severity,
        title = entity.title,
        body = entity.body,
        displayOrder = entity.displayOrder,
        createdAt = entity.createdAt,
    )

    fun toScoreSummary(entity: AnswerScoreEntity): ScoreSummaryDto = ScoreSummaryDto(
        totalScore = entity.totalScore.toInt(),
        structureScore = entity.structureScore.toInt(),
        specificityScore = entity.specificityScore.toInt(),
        technicalAccuracyScore = entity.technicalAccuracyScore.toInt(),
        roleFitScore = entity.roleFitScore.toInt(),
        companyFitScore = entity.companyFitScore.toInt(),
        communicationScore = entity.communicationScore.toInt(),
        evaluationResult = entity.evaluationResult,
    )

    fun toAnalysisDto(entity: AnswerAnalysisEntity): AnswerAnalysisDto = AnswerAnalysisDto(
        answerAttemptId = entity.answerAttemptId,
        overallScore = entity.overallScore,
        depthScore = entity.depthScore,
        clarityScore = entity.clarityScore,
        accuracyScore = entity.accuracyScore,
        exampleScore = entity.exampleScore,
        tradeoffScore = entity.tradeoffScore,
        confidenceScore = entity.confidenceScore,
        strengthSummary = entity.strengthSummary,
        weaknessSummary = entity.weaknessSummary,
        recommendedNextStep = entity.recommendedNextStep,
        createdAt = entity.createdAt,
    )
}
