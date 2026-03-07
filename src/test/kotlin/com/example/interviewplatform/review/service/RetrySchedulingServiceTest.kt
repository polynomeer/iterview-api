package com.example.interviewplatform.review.service

import com.example.interviewplatform.answer.service.AnswerPolicyDecision
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

class RetrySchedulingServiceTest {
    private val repository = mock(ReviewQueueRepository::class.java)
    private val service = RetrySchedulingService(repository)

    @Test
    fun `creates retry schedule based on policy decision`() {
        val now = Instant.parse("2026-03-06T00:00:00Z")
        val expectedScheduled = Instant.parse("2026-03-08T00:00:00Z")
        `when`(
            repository.updatePendingRetry(
                1L,
                2L,
                "pending",
                3L,
                80,
                expectedScheduled,
                now,
            ),
        ).thenReturn(1)

        val scheduled = service.scheduleRetry(
            userId = 1L,
            questionId = 2L,
            answerAttemptId = 3L,
            policy = AnswerPolicyDecision(
                archive = false,
                needsRetry = true,
                retryReasonType = "weak_dimension",
                retryPriority = 80,
                retryDelayDays = 2L,
                weakDimensions = listOf("technicalAccuracy"),
            ),
            now = now,
        )

        assertEquals(expectedScheduled, scheduled)
    }

    @Test
    fun `does not schedule when policy does not require retry`() {
        val scheduled = service.scheduleRetry(
            userId = 1L,
            questionId = 2L,
            answerAttemptId = 3L,
            policy = AnswerPolicyDecision(
                archive = false,
                needsRetry = false,
                retryReasonType = null,
                retryPriority = null,
                retryDelayDays = null,
                weakDimensions = emptyList(),
            ),
            now = Instant.parse("2026-03-06T00:00:00Z"),
        )

        assertNull(scheduled)
    }
}
