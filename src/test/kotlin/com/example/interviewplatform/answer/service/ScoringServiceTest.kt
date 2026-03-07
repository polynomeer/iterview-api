package com.example.interviewplatform.answer.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScoringServiceTest {
    private val scoringService = ScoringService()

    @Test
    fun `returns fail for very short answers`() {
        val result = scoringService.score("short answer")

        assertTrue(result.totalScore < 60)
        assertTrue(result.structureScore in 0..100)
        assertTrue(result.specificityScore in 0..100)
        assertTrue(result.technicalAccuracyScore in 0..100)
        assertTrue(result.roleFitScore in 0..100)
        assertTrue(result.companyFitScore in 0..100)
        assertTrue(result.communicationScore in 0..100)
        assertTrue(result.evaluationResult == "FAIL")
    }

    @Test
    fun `returns pass for detailed technical answers`() {
        val answer = """
            First, I baseline latency and throughput with trace metrics.
            Second, I add caching and idempotent retries to reduce database load.
            We improved latency by 35% and error rate by 22% because queue backpressure was tuned.
            Finally, I document tradeoffs for backend ownership and customer impact.
        """.trimIndent()

        val result = scoringService.score(answer)

        assertTrue(result.totalScore >= 60)
        assertTrue(result.technicalAccuracyScore >= 60)
        assertTrue(result.evaluationResult == "PASS")
    }

    @Test
    fun `caps scores at upper bound for very long keyword rich answers`() {
        val richAnswer = buildString {
            repeat(120) {
                append("First we optimized latency and throughput with cache retry idempotent observability backend customer scale. ")
            }
        }

        val result = scoringService.score(richAnswer)

        assertTrue(result.totalScore in 0..100)
        assertTrue(result.structureScore in 0..100)
        assertTrue(result.specificityScore in 0..100)
        assertTrue(result.technicalAccuracyScore in 0..100)
        assertTrue(result.roleFitScore in 0..100)
        assertTrue(result.companyFitScore in 0..100)
        assertTrue(result.communicationScore in 0..100)
    }
}
