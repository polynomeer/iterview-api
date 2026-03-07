package com.example.interviewplatform.answer.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScoringServiceTest {
    private val scoringService = ScoringService()

    @Test
    fun `returns fail for short answers`() {
        val result = scoringService.score("short")

        assertEquals(33, result.totalScore)
        assertEquals(30, result.structureScore)
        assertEquals(31, result.specificityScore)
        assertEquals("FAIL", result.evaluationResult)
    }

    @Test
    fun `returns pass for long enough answers`() {
        val answer = "I improved API latency by 35 percent because we reduced database round-trips.\n".repeat(8)
        val result = scoringService.score(answer)

        assertEquals(82, result.totalScore)
        assertEquals(82, result.structureScore)
        assertEquals(85, result.specificityScore)
        assertEquals(87, result.technicalAccuracyScore)
        assertEquals("PASS", result.evaluationResult)
    }
}
