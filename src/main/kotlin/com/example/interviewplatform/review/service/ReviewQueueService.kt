package com.example.interviewplatform.review.service

import com.example.interviewplatform.review.entity.ReviewQueueEntity
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ReviewQueueService(
    private val reviewQueueRepository: ReviewQueueRepository,
) {
    fun listPending(userId: Long): List<ReviewQueueEntity> =
        reviewQueueRepository.findByUserIdAndStatusAndScheduledForLessThanEqualOrderByPriorityDescScheduledForAsc(
            userId,
            "pending",
            Instant.now(),
        )
}
