package com.example.interviewplatform.review.service

import com.example.interviewplatform.review.dto.ReviewDecision
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RetrySchedulingService {
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
}
