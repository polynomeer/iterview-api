package com.example.interviewplatform.review.repository

import com.example.interviewplatform.review.entity.ReviewQueueEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ReviewQueueRepository : JpaRepository<ReviewQueueEntity, Long> {
    fun findByUserIdAndStatusAndScheduledForLessThanEqualOrderByPriorityDescScheduledForAsc(
        userId: Long,
        status: String,
        scheduledFor: Instant,
    ): List<ReviewQueueEntity>
}
