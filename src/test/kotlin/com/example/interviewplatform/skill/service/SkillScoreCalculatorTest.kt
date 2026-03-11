package com.example.interviewplatform.skill.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class SkillScoreCalculatorTest {
    private val calculator = SkillScoreCalculator()

    @Test
    fun `calculate score applies weighted formula and rounds to two decimals`() {
        val score = calculator.calculateScore(
            answerQualityAverage = 80.0,
            reviewCompletionRate = 0.75,
            recencyWeight = 1.0,
            confidenceAverage = 0.6,
            depthCoverage = 0.8,
        )

        assertEquals(BigDecimal("79.00"), score)
    }

    @Test
    fun `calculate gap subtracts score from benchmark`() {
        val gap = calculator.calculateGap(BigDecimal("84.00"), BigDecimal("79.00"))

        assertEquals(BigDecimal("5.00"), gap)
    }

    @Test
    fun `experience band maps years conservatively`() {
        assertEquals("JUNIOR", calculator.experienceBandFor(1))
        assertEquals("MID", calculator.experienceBandFor(4))
        assertEquals("SENIOR", calculator.experienceBandFor(8))
        assertEquals("MID", calculator.experienceBandFor(null))
    }

    @Test
    fun `recency weight drops across freshness windows`() {
        val now = Instant.parse("2026-03-11T00:00:00Z")

        assertEquals(1.0, calculator.recencyWeight(now.minusSeconds(2 * 86_400), now))
        assertEquals(0.5, calculator.recencyWeight(now.minusSeconds(14 * 86_400), now))
        assertEquals(0.2, calculator.recencyWeight(now.minusSeconds(45 * 86_400), now))
        assertEquals(0.0, calculator.recencyWeight(null, now))
    }
}
