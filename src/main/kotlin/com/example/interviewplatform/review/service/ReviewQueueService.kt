package com.example.interviewplatform.review.service

import com.example.interviewplatform.answer.repository.AnswerAnalysisRepository
import com.example.interviewplatform.answer.repository.AnswerScoreRepository
import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.review.dto.ReviewQueueActionResponseDto
import com.example.interviewplatform.review.dto.ReviewQueueItemDto
import com.example.interviewplatform.review.entity.ReviewQueueEntity
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ReviewQueueService(
    private val reviewQueueRepository: ReviewQueueRepository,
    private val questionRepository: QuestionRepository,
    private val answerScoreRepository: AnswerScoreRepository,
    private val answerAnalysisRepository: AnswerAnalysisRepository,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val reviewPriorityService: ReviewPriorityService,
    private val clockService: ClockService,
) {
    @Transactional
    fun listPending(userId: Long): List<ReviewQueueItemDto> {
        val now = clockService.now()
        refreshPendingPriorities(userId, now)
        val rows = reviewQueueRepository.findByUserIdAndStatusAndScheduledForLessThanEqualOrderByScheduledForAscPriorityDesc(userId, STATUS_PENDING, now)
        if (rows.isEmpty()) {
            return emptyList()
        }

        val questionById = questionRepository.findAllById(rows.map { it.questionId }).associateBy { it.id }
        return rows.mapNotNull { row ->
            val question = questionById[row.questionId] ?: return@mapNotNull null
            ReviewQueueItemDto(
                id = row.id,
                questionId = row.questionId,
                questionTitle = question.title,
                questionDifficulty = question.difficultyLevel,
                reasonType = row.reasonType,
                priority = row.priority,
                scheduledFor = row.scheduledFor,
                status = row.status,
            )
        }
    }

    private fun refreshPendingPriorities(userId: Long, now: java.time.Instant) {
        val pendingRows = reviewQueueRepository.findByUserIdAndStatus(userId, STATUS_PENDING)
        if (pendingRows.isEmpty()) {
            return
        }

        val scoreByAttemptId = answerScoreRepository.findAllById(pendingRows.map { it.triggerAnswerAttemptId }).associateBy { it.answerAttemptId }
        val analysisByAttemptId = answerAnalysisRepository.findByAnswerAttemptIdIn(pendingRows.map { it.triggerAnswerAttemptId })
            .associateBy { it.answerAttemptId }
        val latestResumeVersionId = resumeRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId).firstOrNull()?.let { resume ->
            resumeVersionRepository.findByResumeIdOrderByVersionNoAsc(resume.id).findLast { it.isActive }?.id
                ?: resumeVersionRepository.findTopByResumeIdOrderByVersionNoDesc(resume.id)?.id
        }
        val riskSeverityByQuestionId = latestResumeVersionId?.let { versionId ->
            resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(versionId)
                .associate { riskItem -> riskItem.linkedQuestionId to riskItem.severity }
        }.orEmpty().filterKeys { it != null }.mapKeys { it.key!! }

        pendingRows.forEach { row ->
            val score = scoreByAttemptId[row.triggerAnswerAttemptId]?.totalScore?.toDouble()
            val confidence = analysisByAttemptId[row.triggerAnswerAttemptId]?.confidenceScore?.toDouble()
            val riskSeverity = riskSeverityByQuestionId[row.questionId]
            val priority = reviewPriorityService.calculate(
                overallScore = score,
                confidenceScore = confidence,
                scheduledFor = row.scheduledFor,
                riskSeverity = riskSeverity,
                now = now,
            )
            val reasonType = when {
                riskSeverity == "HIGH" -> "resume_risk"
                (confidence ?: 100.0) < 50.0 -> "low_confidence"
                row.scheduledFor.isBefore(now) -> row.reasonType
                else -> row.reasonType
            }
            if (priority != row.priority || reasonType != row.reasonType) {
                reviewQueueRepository.save(
                    ReviewQueueEntity(
                        id = row.id,
                        userId = row.userId,
                        questionId = row.questionId,
                        triggerAnswerAttemptId = row.triggerAnswerAttemptId,
                        reasonType = reasonType,
                        priority = priority,
                        scheduledFor = row.scheduledFor,
                        status = row.status,
                        createdAt = row.createdAt,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    @Transactional
    fun skip(userId: Long, queueId: Long): ReviewQueueActionResponseDto =
        updateStatus(userId, queueId, STATUS_SKIPPED)

    @Transactional
    fun done(userId: Long, queueId: Long): ReviewQueueActionResponseDto =
        updateStatus(userId, queueId, STATUS_DONE)

    private fun updateStatus(userId: Long, queueId: Long, nextStatus: String): ReviewQueueActionResponseDto {
        val now = clockService.now()
        val updated = reviewQueueRepository.updateStatusById(
            id = queueId,
            userId = userId,
            expectedStatus = STATUS_PENDING,
            status = nextStatus,
            updatedAt = now,
        )
        if (updated == 0) {
            reviewQueueRepository.findByIdAndUserId(queueId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Review queue not found: $queueId")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Review queue is not pending: $queueId")
        }

        return ReviewQueueActionResponseDto(
            id = queueId,
            status = nextStatus,
            updatedAt = now,
        )
    }

    private companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_DONE = "done"
    }
}
