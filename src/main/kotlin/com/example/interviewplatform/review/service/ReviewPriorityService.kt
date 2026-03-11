package com.example.interviewplatform.review.service

import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

@Service
class ReviewPriorityService {
    fun calculate(
        overallScore: Double?,
        confidenceScore: Double?,
        scheduledFor: Instant,
        riskSeverity: String?,
        now: Instant,
    ): Int {
        val scorePenalty = (100.0 - (overallScore ?: 0.0)).coerceIn(0.0, 100.0)
        val confidencePenalty = (100.0 - (confidenceScore ?: 50.0)).coerceIn(0.0, 100.0)
        val overdueWeight = overdueScore(scheduledFor, now)
        val riskWeight = when (riskSeverity?.uppercase()) {
            "HIGH" -> 100.0
            "MEDIUM" -> 60.0
            "LOW" -> 20.0
            else -> 0.0
        }
        return (
            scorePenalty * 0.4 +
                confidencePenalty * 0.2 +
                overdueWeight * 0.2 +
                riskWeight * 0.2
            ).roundToInt()
    }

    private fun overdueScore(scheduledFor: Instant, now: Instant): Double {
        if (!scheduledFor.isBefore(now)) {
            return 0.0
        }
        val overdueDays = Duration.between(scheduledFor, now).toDays().coerceAtLeast(0)
        return (overdueDays * 20.0).coerceAtMost(100.0)
    }
}
