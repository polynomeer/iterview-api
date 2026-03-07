package com.example.interviewplatform.review.service

import com.example.interviewplatform.answer.service.AnswerPolicyDecision
import com.example.interviewplatform.review.entity.ReviewQueueEntity
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
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

    @Test
    fun `creates new pending retry row when update does not match`() {
        val now = Instant.parse("2026-03-06T00:00:00Z")
        `when`(
            repository.updatePendingRetry(
                1L,
                2L,
                "pending",
                3L,
                60,
                Instant.parse("2026-03-09T00:00:00Z"),
                now,
            ),
        ).thenReturn(0)

        val scheduled = service.scheduleRetry(
            userId = 1L,
            questionId = 2L,
            answerAttemptId = 3L,
            policy = AnswerPolicyDecision(
                archive = false,
                needsRetry = true,
                retryReasonType = "weak_dimension",
                retryPriority = 60,
                retryDelayDays = 3L,
                weakDimensions = listOf("roleFit"),
            ),
            now = now,
        )

        assertEquals(Instant.parse("2026-03-09T00:00:00Z"), scheduled)
        val captor = ArgumentCaptor.forClass(ReviewQueueEntity::class.java)
        verify(repository, times(1)).save(captor.capture())
        assertEquals(1L, captor.value.userId)
        assertEquals(2L, captor.value.questionId)
        assertEquals(3L, captor.value.triggerAnswerAttemptId)
        assertEquals("weak_dimension", captor.value.reasonType)
        assertEquals(60, captor.value.priority)
        assertEquals("pending", captor.value.status)
    }

    @Test
    fun `clear pending marks queue rows done`() {
        val now = Instant.parse("2026-03-06T00:00:00Z")

        service.clearPendingForArchived(userId = 5L, questionId = 7L, now = now)

        verify(repository).updateStatusForQuestion(
            userId = 5L,
            questionId = 7L,
            currentStatus = "pending",
            newStatus = "done",
            updatedAt = now,
        )
    }
}
