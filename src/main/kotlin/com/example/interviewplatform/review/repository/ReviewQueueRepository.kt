package com.example.interviewplatform.review.repository

import com.example.interviewplatform.review.entity.ReviewQueueEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface ReviewQueueRepository : JpaRepository<ReviewQueueEntity, Long> {
    fun findByUserIdAndStatus(userId: Long, status: String): List<ReviewQueueEntity>

    fun findByUserIdAndStatusAndScheduledForLessThanEqualOrderByScheduledForAscPriorityDesc(
        userId: Long,
        status: String,
        scheduledFor: Instant,
    ): List<ReviewQueueEntity>

    fun findByIdAndUserId(id: Long, userId: Long): ReviewQueueEntity?

    @Modifying
    @Query(
        """
        update ReviewQueueEntity r
        set r.triggerAnswerAttemptId = :triggerAnswerAttemptId,
            r.reasonType = :reasonType,
            r.priority = :priority,
            r.scheduledFor = :scheduledFor,
            r.updatedAt = :updatedAt
        where r.userId = :userId and r.questionId = :questionId and r.status = :status
        """,
    )
    fun updatePendingRetry(
        userId: Long,
        questionId: Long,
        status: String,
        triggerAnswerAttemptId: Long,
        reasonType: String,
        priority: Int,
        scheduledFor: Instant,
        updatedAt: Instant,
    ): Int

    @Modifying
    @Query(
        """
        update ReviewQueueEntity r
        set r.status = :newStatus,
            r.updatedAt = :updatedAt
        where r.userId = :userId and r.questionId = :questionId and r.status = :currentStatus
        """,
    )
    fun updateStatusForQuestion(
        userId: Long,
        questionId: Long,
        currentStatus: String,
        newStatus: String,
        updatedAt: Instant,
    ): Int

    @Modifying
    @Query(
        """
        update ReviewQueueEntity r
        set r.status = :status,
            r.updatedAt = :updatedAt
        where r.id = :id and r.userId = :userId and r.status = :expectedStatus
        """,
    )
    fun updateStatusById(
        id: Long,
        userId: Long,
        expectedStatus: String,
        status: String,
        updatedAt: Instant,
    ): Int
}
