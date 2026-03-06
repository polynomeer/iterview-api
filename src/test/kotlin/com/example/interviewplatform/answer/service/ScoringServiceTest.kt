package com.example.interviewplatform.answer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScoringServiceTest {
    private val scoringService = ScoringService()

    @Test
    fun `returns fail for short answers`() {
        val result = scoringService.score("short")

        assertEquals(40, result.totalScore)
        assertEquals("FAIL", result.evaluationResult)
    }

    @Test
    fun `returns pass for long enough answers`() {
        val answer = "a".repeat(300)
        val result = scoringService.score(answer)

        assertEquals(70, result.totalScore)
        assertEquals("PASS", result.evaluationResult)
    }
}
