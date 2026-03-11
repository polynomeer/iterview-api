package com.example.interviewplatform.skill.service

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

@Component
class SkillScoreCalculator {
    fun calculateScore(
        answerQualityAverage: Double,
        reviewCompletionRate: Double,
        recencyWeight: Double,
        confidenceAverage: Double,
        depthCoverage: Double,
    ): BigDecimal {
        val score = (
            answerQualityAverage * 0.5 +
                reviewCompletionRate * 20.0 +
                recencyWeight * 10.0 +
                confidenceAverage * 10.0 +
                depthCoverage * 10.0
            ).coerceIn(0.0, 100.0)
        return score.toBigDecimal().setScale(2, RoundingMode.HALF_UP)
    }

    fun calculateGap(benchmarkScore: BigDecimal?, score: BigDecimal): BigDecimal? =
        benchmarkScore?.subtract(score)?.setScale(2, RoundingMode.HALF_UP)

    fun experienceBandFor(yearsOfExperience: Int?): String = when {
        yearsOfExperience == null -> "MID"
        yearsOfExperience >= 6 -> "SENIOR"
        yearsOfExperience >= 2 -> "MID"
        else -> "JUNIOR"
    }

    fun average(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()

    fun recencyWeight(lastSubmittedAt: Instant?, now: Instant): Double = when {
        lastSubmittedAt == null -> 0.0
        Duration.between(lastSubmittedAt, now).toDays() <= 7 -> 1.0
        Duration.between(lastSubmittedAt, now).toDays() <= 30 -> 0.5
        else -> 0.2
    }
}
