package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class ScoringService {
    fun score(answerText: String): ScoreSummaryDto {
        val text = answerText.trim()
        val wordCount = words(text).size
        val sentenceCount = sentenceCount(text)
        val lineCount = text.lines().count { it.isNotBlank() }
        val numberHits = Regex("\\b\\d+(?:\\.\\d+)?\\b|%|ms|s|x").findAll(text).count()

        val technicalHits = keywordHits(text, TECHNICAL_KEYWORDS)
        val roleHits = keywordHits(text, ROLE_KEYWORDS)
        val companyHits = keywordHits(text, COMPANY_KEYWORDS)
        val transitionHits = keywordHits(text, TRANSITION_KEYWORDS)

        val baseCoverage = clamp((wordCount * 0.7).roundToInt())

        val structureScore = clamp(
            25 + (lineCount * 8) + (sentenceCount * 4) + (transitionHits * 5) + (if (text.contains("\n")) 6 else 0),
        )
        val specificityScore = clamp(20 + ((wordCount * 0.45).roundToInt()) + (numberHits * 7) + (if (text.contains("for example", true)) 8 else 0))
        val technicalAccuracyScore = clamp(18 + ((wordCount * 0.45).roundToInt()) + (technicalHits * 8))
        val roleFitScore = clamp(20 + ((wordCount * 0.35).roundToInt()) + (roleHits * 10))
        val companyFitScore = clamp(15 + ((wordCount * 0.25).roundToInt()) + (companyHits * 12))

        val avgSentenceLength = if (sentenceCount == 0) wordCount else wordCount / sentenceCount
        val communicationBonus = when {
            avgSentenceLength in 8..24 -> 18
            avgSentenceLength in 5..30 -> 10
            else -> 3
        }
        val communicationScore = clamp(22 + ((wordCount * 0.4).roundToInt()) + communicationBonus)

        val totalScore = weightedTotal(
            structureScore = structureScore,
            specificityScore = specificityScore,
            technicalAccuracyScore = technicalAccuracyScore,
            roleFitScore = roleFitScore,
            companyFitScore = companyFitScore,
            communicationScore = communicationScore,
            baseCoverage = baseCoverage,
        )
        val evaluationResult = if (totalScore >= PASS_THRESHOLD) "PASS" else "FAIL"

        return ScoreSummaryDto(
            totalScore = totalScore,
            structureScore = structureScore,
            specificityScore = specificityScore,
            technicalAccuracyScore = technicalAccuracyScore,
            roleFitScore = roleFitScore,
            companyFitScore = companyFitScore,
            communicationScore = communicationScore,
            evaluationResult = evaluationResult,
        )
    }

    private fun weightedTotal(
        structureScore: Int,
        specificityScore: Int,
        technicalAccuracyScore: Int,
        roleFitScore: Int,
        companyFitScore: Int,
        communicationScore: Int,
        baseCoverage: Int,
    ): Int {
        val weighted =
            (structureScore * 0.16) +
                (specificityScore * 0.20) +
                (technicalAccuracyScore * 0.24) +
                (roleFitScore * 0.14) +
                (companyFitScore * 0.10) +
                (communicationScore * 0.16)

        val coverageAdjustment = (baseCoverage.coerceAtMost(40) * 0.1)
        return clamp((weighted + coverageAdjustment).roundToInt())
    }

    private fun words(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun sentenceCount(text: String): Int = text.split(Regex("[.!?]+"))
        .map { it.trim() }
        .count { it.isNotEmpty() }

    private fun keywordHits(text: String, keywords: Set<String>): Int {
        val lower = text.lowercase()
        return keywords.count { keyword -> lower.contains(keyword) }
    }

    private fun clamp(score: Int): Int = score.coerceIn(0, 100)

    private companion object {
        const val PASS_THRESHOLD = 60

        val TECHNICAL_KEYWORDS = setOf(
            "latency", "throughput", "consistency", "cache", "database", "replica", "queue",
            "index", "partition", "load balancer", "retry", "idempotent", "observability",
        )

        val ROLE_KEYWORDS = setOf(
            "backend", "frontend", "fullstack", "ownership", "stakeholder", "mentoring", "lead",
        )

        val COMPANY_KEYWORDS = setOf(
            "customer", "scale", "operational excellence", "leadership", "bar raiser", "frugality",
        )

        val TRANSITION_KEYWORDS = setOf(
            "first", "second", "third", "finally", "therefore", "because", "so that",
        )
    }
}
