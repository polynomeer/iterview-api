package com.example.interviewplatform.review.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ReviewPriorityServiceTest {
    private val service = ReviewPriorityService()

    @Test
    fun `higher risk and lower confidence produce higher priority`() {
        val now = Instant.parse("2026-03-11T00:00:00Z")
        val lowRiskPriority = service.calculate(
            overallScore = 70.0,
            confidenceScore = 80.0,
            scheduledFor = now.minusSeconds(3_600),
            riskSeverity = "LOW",
            now = now,
        )
        val highRiskPriority = service.calculate(
            overallScore = 70.0,
            confidenceScore = 40.0,
            scheduledFor = now.minusSeconds(3_600),
            riskSeverity = "HIGH",
            now = now,
        )

        assertTrue(highRiskPriority > lowRiskPriority)
    }

    @Test
    fun `older overdue item increases priority`() {
        val now = Instant.parse("2026-03-11T00:00:00Z")
        val recent = service.calculate(50.0, 50.0, now.minusSeconds(3_600), null, now)
        val overdue = service.calculate(50.0, 50.0, now.minusSeconds(86_400 * 5), null, now)

        assertTrue(overdue > recent)
    }
}
