package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnswerPolicyServiceTest {
    private val service = AnswerPolicyService()

    @Test
    fun `archive path when score and dimensions are strong`() {
        val decision = service.evaluate(
            score = score(
                total = 90,
                structure = 82,
                specificity = 84,
                technical = 88,
                roleFit = 78,
                companyFit = 72,
                communication = 80,
            ),
            attemptCount = 3,
            answerMode = "text",
        )

        assertTrue(decision.archive)
        assertFalse(decision.needsRetry)
        assertEquals(null, decision.retryReasonType)
    }

    @Test
    fun `retry path when total score is low`() {
        val decision = service.evaluate(
            score = score(
                total = 42,
                structure = 48,
                specificity = 46,
                technical = 43,
                roleFit = 51,
                companyFit = 44,
                communication = 47,
            ),
            attemptCount = 1,
            answerMode = "text",
        )

        assertFalse(decision.archive)
        assertTrue(decision.needsRetry)
        assertEquals("low_total", decision.retryReasonType)
        assertEquals(100, decision.retryPriority)
        assertEquals(1L, decision.retryDelayDays)
    }

    @Test
    fun `pass-but-not-archive path`() {
        val decision = service.evaluate(
            score = score(
                total = 74,
                structure = 70,
                specificity = 72,
                technical = 76,
                roleFit = 68,
                companyFit = 66,
                communication = 73,
            ),
            attemptCount = 2,
            answerMode = "text",
        )

        assertFalse(decision.archive)
        assertFalse(decision.needsRetry)
    }

    @Test
    fun `weak-dimension-triggered retry despite passing total`() {
        val decision = service.evaluate(
            score = score(
                total = 66,
                structure = 74,
                specificity = 72,
                technical = 32,
                roleFit = 68,
                companyFit = 60,
                communication = 70,
            ),
            attemptCount = 2,
            answerMode = "text",
        )

        assertFalse(decision.archive)
        assertTrue(decision.needsRetry)
        assertEquals("weak_dimension", decision.retryReasonType)
        assertTrue(decision.weakDimensions.contains("technicalAccuracy"))
    }

    @Test
    fun `skip mode always schedules high priority retry`() {
        val decision = service.evaluate(
            score = score(
                total = 95,
                structure = 95,
                specificity = 95,
                technical = 95,
                roleFit = 95,
                companyFit = 95,
                communication = 95,
            ),
            attemptCount = 5,
            answerMode = "skip",
        )

        assertFalse(decision.archive)
        assertTrue(decision.needsRetry)
        assertEquals("skipped_or_unanswered", decision.retryReasonType)
        assertEquals(100, decision.retryPriority)
        assertEquals(1L, decision.retryDelayDays)
    }

    @Test
    fun `strong score does not archive before minimum attempt count`() {
        val decision = service.evaluate(
            score = score(
                total = 92,
                structure = 90,
                specificity = 90,
                technical = 92,
                roleFit = 86,
                companyFit = 82,
                communication = 90,
            ),
            attemptCount = 1,
            answerMode = "text",
        )

        assertFalse(decision.archive)
        assertFalse(decision.needsRetry)
    }

    private fun score(
        total: Int,
        structure: Int,
        specificity: Int,
        technical: Int,
        roleFit: Int,
        companyFit: Int,
        communication: Int,
    ): ScoreSummaryDto = ScoreSummaryDto(
        totalScore = total,
        structureScore = structure,
        specificityScore = specificity,
        technicalAccuracyScore = technical,
        roleFitScore = roleFit,
        companyFitScore = companyFit,
        communicationScore = communication,
        evaluationResult = if (total >= 60) "PASS" else "FAIL",
    )
}
