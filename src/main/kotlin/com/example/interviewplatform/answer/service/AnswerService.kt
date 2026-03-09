package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.AnswerAttemptDetailResponseDto
import com.example.interviewplatform.answer.dto.AnswerAttemptListItemDto
import com.example.interviewplatform.answer.dto.AnswerFeedbackItemDto
import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import com.example.interviewplatform.answer.dto.SubmitAnswerRequest
import com.example.interviewplatform.answer.dto.SubmitAnswerResponseDto
import com.example.interviewplatform.answer.entity.AnswerAttemptEntity
import com.example.interviewplatform.answer.entity.AnswerFeedbackItemEntity
import com.example.interviewplatform.answer.entity.AnswerScoreEntity
import com.example.interviewplatform.answer.mapper.AnswerMapper
import com.example.interviewplatform.answer.repository.AnswerAttemptRepository
import com.example.interviewplatform.answer.repository.AnswerFeedbackItemRepository
import com.example.interviewplatform.answer.repository.AnswerScoreRepository
import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.question.dto.UserProgressSummaryDto
import com.example.interviewplatform.question.entity.UserQuestionProgressEntity
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.review.service.RetrySchedulingService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AnswerService(
    private val answerAttemptRepository: AnswerAttemptRepository,
    private val answerScoreRepository: AnswerScoreRepository,
    private val answerFeedbackItemRepository: AnswerFeedbackItemRepository,
    private val questionRepository: QuestionRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeRepository: ResumeRepository,
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val scoringService: ScoringService,
    private val answerPolicyService: AnswerPolicyService,
    private val retrySchedulingService: RetrySchedulingService,
    private val clockService: ClockService,
) {
    @Transactional
    fun submitAnswer(userId: Long, questionId: Long, request: SubmitAnswerRequest): SubmitAnswerResponseDto {
        requireActiveQuestion(questionId)
        validateResumeVersionOwnership(userId, request.resumeVersionId)

        val now = clockService.now()
        val latestAttempt = answerAttemptRepository.findTopByUserIdAndQuestionIdOrderByAttemptNoDesc(userId, questionId)
        val attemptNo = (latestAttempt?.attemptNo ?: 0) + 1

        val savedAttempt = answerAttemptRepository.save(
            AnswerAttemptEntity(
                userId = userId,
                questionId = questionId,
                resumeVersionId = request.resumeVersionId,
                sourceDailyCardId = null,
                attemptNo = attemptNo,
                answerMode = request.answerMode.trim().lowercase(),
                contentText = request.contentText.trim(),
                submittedAt = now,
                createdAt = now,
            ),
        )

        val score = scoringService.score(savedAttempt.contentText)
        answerScoreRepository.save(toScoreEntity(savedAttempt.id, score, now))

        val feedbackEntities = buildFeedback(score, savedAttempt.id, now)
        val savedFeedback = answerFeedbackItemRepository.saveAll(feedbackEntities)

        val previousProgress = userQuestionProgressRepository.findByUserIdAndQuestionId(userId, questionId)
        val totalAttemptCount = (previousProgress?.totalAttemptCount ?: 0) + 1
        val policy = answerPolicyService.evaluate(
            score = score,
            attemptCount = totalAttemptCount,
            answerMode = savedAttempt.answerMode,
        )
        val shouldArchive = policy.archive

        val nextReviewAt = if (shouldArchive) {
            retrySchedulingService.clearPendingForArchived(userId, questionId, now)
            null
        } else {
            retrySchedulingService.scheduleRetry(
                userId = userId,
                questionId = questionId,
                answerAttemptId = savedAttempt.id,
                policy = policy,
                now = now,
            )
        }

        val progressStatus = resolveProgressStatus(shouldArchive, nextReviewAt)
        val updatedProgress = upsertProgress(
            previousProgress = previousProgress,
            attempt = savedAttempt,
            score = score,
            totalAttemptCount = totalAttemptCount,
            progressStatus = progressStatus,
            nextReviewAt = nextReviewAt,
            shouldArchive = shouldArchive,
            now = now,
        )
        userQuestionProgressRepository.save(updatedProgress)

        return AnswerMapper.toSubmitResponse(
            answerAttemptId = savedAttempt.id,
            scoreSummary = score,
            feedback = savedFeedback.map(AnswerMapper::toFeedbackDto),
            progressStatus = progressStatus,
            nextReviewAt = nextReviewAt,
            archiveDecision = shouldArchive,
        )
    }

    @Transactional(readOnly = true)
    fun listQuestionAnswers(userId: Long, questionId: Long): List<AnswerAttemptListItemDto> {
        requireActiveQuestion(questionId)
        val attempts = answerAttemptRepository.findByUserIdAndQuestionIdOrderByAttemptNoDesc(userId, questionId)
        if (attempts.isEmpty()) {
            return emptyList()
        }

        val scoresByAttemptId = answerScoreRepository.findAllById(attempts.map { it.id }).associateBy { it.answerAttemptId }
        return attempts.mapNotNull { attempt ->
            scoresByAttemptId[attempt.id]?.let { scoreEntity ->
                AnswerMapper.toListItem(attempt, AnswerMapper.toScoreSummary(scoreEntity))
            }
        }
    }

    @Transactional(readOnly = true)
    fun getAnswerAttempt(userId: Long, answerAttemptId: Long): AnswerAttemptDetailResponseDto {
        val attempt = answerAttemptRepository.findByIdAndUserId(answerAttemptId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Answer attempt not found: $answerAttemptId")
        val score = answerScoreRepository.findById(answerAttemptId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Answer score not found: $answerAttemptId") }
        val feedback = answerFeedbackItemRepository.findByAnswerAttemptIdOrderByDisplayOrderAsc(answerAttemptId)
        val progress = userQuestionProgressRepository.findByUserIdAndQuestionId(userId, attempt.questionId)

        return AnswerMapper.toDetailResponse(
            attempt = attempt,
            score = AnswerMapper.toScoreSummary(score),
            feedback = feedback.map(AnswerMapper::toFeedbackDto),
            progressSummary = progress?.let(::toProgressSummary),
        )
    }

    private fun requireActiveQuestion(questionId: Long) {
        questionRepository.findByIdAndIsActiveTrue(questionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: $questionId")
    }

    private fun validateResumeVersionOwnership(userId: Long, resumeVersionId: Long?) {
        if (resumeVersionId == null) {
            return
        }

        val version = resumeVersionRepository.findById(resumeVersionId)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resumeVersionId: $resumeVersionId") }
        resumeRepository.findByIdAndUserId(version.resumeId, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resumeVersionId: $resumeVersionId")
    }

    private fun toScoreEntity(answerAttemptId: Long, score: ScoreSummaryDto, now: java.time.Instant): AnswerScoreEntity =
        AnswerScoreEntity(
            answerAttemptId = answerAttemptId,
            totalScore = score.totalScore.toBigDecimal(),
            structureScore = score.structureScore.toBigDecimal(),
            specificityScore = score.specificityScore.toBigDecimal(),
            technicalAccuracyScore = score.technicalAccuracyScore.toBigDecimal(),
            roleFitScore = score.roleFitScore.toBigDecimal(),
            companyFitScore = score.companyFitScore.toBigDecimal(),
            communicationScore = score.communicationScore.toBigDecimal(),
            evaluationResult = score.evaluationResult,
            evaluatedAt = now,
        )

    private fun buildFeedback(score: ScoreSummaryDto, answerAttemptId: Long, now: java.time.Instant): List<AnswerFeedbackItemEntity> {
        val primary = if (score.evaluationResult == PASS_RESULT) {
            AnswerFeedbackItemEntity(
                answerAttemptId = answerAttemptId,
                feedbackType = "strength",
                severity = "info",
                title = "Good baseline answer",
                body = "Your answer covers the question and keeps a coherent flow.",
                displayOrder = 1,
                createdAt = now,
            )
        } else {
            AnswerFeedbackItemEntity(
                answerAttemptId = answerAttemptId,
                feedbackType = "improvement",
                severity = "high",
                title = "Add clearer structure",
                body = "Use a short intro, key points, and a concise conclusion.",
                displayOrder = 1,
                createdAt = now,
            )
        }

        val secondary = if (score.specificityScore >= 60) {
            AnswerFeedbackItemEntity(
                answerAttemptId = answerAttemptId,
                feedbackType = "next_step",
                severity = "low",
                title = "Raise company relevance",
                body = "Connect your example to the target company context for stronger fit.",
                displayOrder = 2,
                createdAt = now,
            )
        } else {
            AnswerFeedbackItemEntity(
                answerAttemptId = answerAttemptId,
                feedbackType = "improvement",
                severity = "medium",
                title = "Increase specificity",
                body = "Include concrete examples, metrics, and technical decisions.",
                displayOrder = 2,
                createdAt = now,
            )
        }

        return listOf(primary, secondary)
    }

    private fun resolveProgressStatus(shouldArchive: Boolean, nextReviewAt: java.time.Instant?): String {
        if (shouldArchive) {
            return STATUS_ARCHIVED
        }
        if (nextReviewAt != null) {
            return STATUS_RETRY_PENDING
        }
        return STATUS_IN_PROGRESS
    }

    private fun upsertProgress(
        previousProgress: UserQuestionProgressEntity?,
        attempt: AnswerAttemptEntity,
        score: ScoreSummaryDto,
        totalAttemptCount: Int,
        progressStatus: String,
        nextReviewAt: java.time.Instant?,
        shouldArchive: Boolean,
        now: java.time.Instant,
    ): UserQuestionProgressEntity {
        val latestScore = score.totalScore.toBigDecimal()
        val previousBest = previousProgress?.bestScore
        val isBest = previousBest == null || latestScore > previousBest
        val bestAttemptId = if (isBest) attempt.id else previousProgress!!.bestAnswerAttemptId
        val bestScore = if (isBest) latestScore else previousBest!!
        val unansweredCount = (previousProgress?.unansweredCount ?: 0) + unansweredIncrementFor(attempt.answerMode)

        return UserQuestionProgressEntity(
            id = previousProgress?.id ?: 0,
            userId = attempt.userId,
            questionId = attempt.questionId,
            latestAnswerAttemptId = attempt.id,
            bestAnswerAttemptId = bestAttemptId,
            latestScore = latestScore,
            bestScore = bestScore,
            totalAttemptCount = totalAttemptCount,
            unansweredCount = unansweredCount,
            currentStatus = progressStatus,
            archivedAt = if (shouldArchive) now else null,
            lastAnsweredAt = now,
            nextReviewAt = nextReviewAt,
            masteryLevel = masteryLevelFor(score.totalScore),
            createdAt = previousProgress?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private fun toProgressSummary(progress: UserQuestionProgressEntity): UserProgressSummaryDto = UserProgressSummaryDto(
        currentStatus = progress.currentStatus,
        latestScore = progress.latestScore,
        bestScore = progress.bestScore,
        totalAttemptCount = progress.totalAttemptCount,
        lastAnsweredAt = progress.lastAnsweredAt,
        nextReviewAt = progress.nextReviewAt,
        masteryLevel = progress.masteryLevel,
    )

    private fun masteryLevelFor(totalScore: Int): String = when {
        totalScore >= 85 -> "advanced"
        totalScore >= 70 -> "intermediate"
        else -> "beginner"
    }

    private fun unansweredIncrementFor(answerMode: String): Int = when (answerMode.trim().lowercase()) {
        "skip", "unanswered" -> 1
        else -> 0
    }

    private companion object {
        const val PASS_RESULT = "PASS"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_RETRY_PENDING = "retry_pending"
        const val STATUS_ARCHIVED = "archived"
    }
}
