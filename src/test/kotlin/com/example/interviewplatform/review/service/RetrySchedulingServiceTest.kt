package com.example.interviewplatform.review.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RetrySchedulingServiceTest {
    @Test
    fun `creates retry schedule for low score`() {
        val fixedNow = Instant.parse("2026-03-06T00:00:00Z")
        val service = RetrySchedulingService()

        val decision = service.scheduleForScore(35, fixedNow)

        assertTrue(decision.needsRetry)
        assertEquals(Instant.parse("2026-03-07T00:00:00Z"), decision.scheduledFor)
    }

    @Test
    fun `does not schedule retry for passing score`() {
        val fixedNow = Instant.parse("2026-03-06T00:00:00Z")
        val service = RetrySchedulingService()

        val decision = service.scheduleForScore(70, fixedNow)

        assertEquals(false, decision.needsRetry)
        assertEquals(null, decision.scheduledFor)
    }
}
