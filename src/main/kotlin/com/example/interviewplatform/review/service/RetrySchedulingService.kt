package com.example.interviewplatform.review.service

import com.example.interviewplatform.answer.service.AnswerPolicyDecision
import com.example.interviewplatform.review.entity.ReviewQueueEntity
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RetrySchedulingService(
    private val reviewQueueRepository: ReviewQueueRepository,
) {
    fun scheduleRetry(
        userId: Long,
        questionId: Long,
        answerAttemptId: Long,
        policy: AnswerPolicyDecision,
        now: Instant,
    ): Instant? {
        if (!policy.needsRetry || policy.retryDelayDays == null || policy.retryPriority == null || policy.retryReasonType == null) {
            return null
        }

        val scheduledFor = now.plus(policy.retryDelayDays, ChronoUnit.DAYS)
        val updatedRows = reviewQueueRepository.updatePendingRetry(
            userId = userId,
            questionId = questionId,
            status = STATUS_PENDING,
            triggerAnswerAttemptId = answerAttemptId,
            reasonType = policy.retryReasonType,
            priority = policy.retryPriority,
            scheduledFor = scheduledFor,
            updatedAt = now,
        )
        if (updatedRows == 0) {
            reviewQueueRepository.save(
                ReviewQueueEntity(
                    userId = userId,
                    questionId = questionId,
                    triggerAnswerAttemptId = answerAttemptId,
                    reasonType = policy.retryReasonType,
                    priority = policy.retryPriority,
                    scheduledFor = scheduledFor,
                    status = STATUS_PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return scheduledFor
    }

    fun clearPendingForArchived(userId: Long, questionId: Long, now: Instant) {
        reviewQueueRepository.updateStatusForQuestion(
            userId = userId,
            questionId = questionId,
            currentStatus = STATUS_PENDING,
            newStatus = STATUS_DONE,
            updatedAt = now,
        )
    }

    private companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DONE = "done"
    }
}
