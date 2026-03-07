package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class ScoringService {
    fun score(answerText: String): ScoreSummaryDto {
        val text = answerText.trim()
        val normalizedLength = text.length
        val base = when {
            normalizedLength >= 700 -> 92
            normalizedLength >= 500 -> 84
            normalizedLength >= 320 -> 74
            normalizedLength >= 180 -> 64
            normalizedLength >= 100 -> 54
            else -> 38
        }
        val specificityBoost = if (Regex("\\d").containsMatchIn(text)) 4 else -4
        val structureBoost = if (text.contains("\n")) 3 else -3
        val technicalBoost = if (text.contains("because", ignoreCase = true)) 3 else -3

        val structureScore = clamp(base - 5 + structureBoost)
        val specificityScore = clamp(base - 3 + specificityBoost)
        val technicalAccuracyScore = clamp(base + technicalBoost)
        val roleFitScore = clamp(base - 4)
        val companyFitScore = clamp(base - 8)
        val communicationScore = clamp(base - 2)
        val totalScore = listOf(
            structureScore,
            specificityScore,
            technicalAccuracyScore,
            roleFitScore,
            companyFitScore,
            communicationScore,
        ).average().roundToInt()
        val evaluationResult = if (totalScore >= 60) "PASS" else "FAIL"

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

    private fun clamp(score: Int): Int = score.coerceIn(0, 100)
}
