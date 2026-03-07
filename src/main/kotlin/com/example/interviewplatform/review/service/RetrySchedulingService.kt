package com.example.interviewplatform.review.service

import com.example.interviewplatform.review.dto.ReviewDecision
import com.example.interviewplatform.review.entity.ReviewQueueEntity
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RetrySchedulingService(
    private val reviewQueueRepository: ReviewQueueRepository,
) {
    fun scheduleForScore(score: Int, now: Instant = Instant.now()): ReviewDecision {
        if (score >= 60) {
            return ReviewDecision(needsRetry = false, scheduledFor = null)
        }
        val days = when {
            score < 40 -> 1L
            score < 50 -> 2L
            else -> 3L
        }
        val scheduled = now.plus(days, ChronoUnit.DAYS)
        return ReviewDecision(needsRetry = true, scheduledFor = scheduled)
    }

    fun scheduleRetry(
        userId: Long,
        questionId: Long,
        answerAttemptId: Long,
        score: Int,
        now: Instant,
    ): Instant? {
        val decision = scheduleForScore(score, now)
        if (!decision.needsRetry || decision.scheduledFor == null) {
            return null
        }

        val priority = priorityForScore(score)
        val updatedRows = reviewQueueRepository.updatePendingRetry(
            userId = userId,
            questionId = questionId,
            status = STATUS_PENDING,
            triggerAnswerAttemptId = answerAttemptId,
            priority = priority,
            scheduledFor = decision.scheduledFor,
            updatedAt = now,
        )
        if (updatedRows == 0) {
            reviewQueueRepository.save(
                ReviewQueueEntity(
                    userId = userId,
                    questionId = questionId,
                    triggerAnswerAttemptId = answerAttemptId,
                    reasonType = REASON_LOW_SCORE,
                    priority = priority,
                    scheduledFor = decision.scheduledFor,
                    status = STATUS_PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return decision.scheduledFor
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

    private fun priorityForScore(score: Int): Int = when {
        score < 40 -> 100
        score < 50 -> 80
        else -> 60
    }

    private companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DONE = "done"
        const val REASON_LOW_SCORE = "low_score"
    }
}
