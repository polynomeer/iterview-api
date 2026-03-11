package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.AnswerFeedbackItemDto
import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import com.example.interviewplatform.answer.entity.AnswerAnalysisEntity
import com.example.interviewplatform.answer.entity.AnswerAttemptEntity
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
class AnswerAnalysisService {
    fun analyze(
        attempt: AnswerAttemptEntity,
        score: ScoreSummaryDto,
        feedback: List<AnswerFeedbackItemDto>,
        now: Instant,
    ): AnswerAnalysisEntity {
        val normalizedLength = attempt.contentText.trim().length.coerceAtMost(1200).toDouble() / 1200.0
        val hasNumericEvidence = attempt.contentText.any { it.isDigit() }
        val mentionsTradeoff = TRADEOFF_HINTS.any { attempt.contentText.contains(it, ignoreCase = true) }
        val mentionsExample = EXAMPLE_HINTS.any { attempt.contentText.contains(it, ignoreCase = true) } || hasNumericEvidence

        val depthScore = weighted(score.technicalAccuracyScore * 0.6 + normalizedLength * 40.0)
        val clarityScore = weighted(score.structureScore.toDouble())
        val accuracyScore = weighted(score.technicalAccuracyScore.toDouble())
        val exampleScore = weighted(if (mentionsExample) score.specificityScore + 10.0 else score.specificityScore.toDouble() - 5.0)
        val tradeoffScore = weighted(if (mentionsTradeoff) score.companyFitScore + 12.0 else score.companyFitScore.toDouble() - 8.0)
        val confidenceScore = weighted(
            when (attempt.answerMode) {
                "skip", "unanswered" -> 10.0
                else -> score.totalScore * 0.7 + normalizedLength * 20.0
            },
        )

        val strengthSummary = when {
            score.totalScore >= 85 -> "Strong answer with clear structure and defensible technical choices."
            score.totalScore >= 65 -> "Solid baseline answer that covers the prompt and shows partial depth."
            else -> feedback.firstOrNull()?.body ?: "The answer shows initial understanding but needs stronger structure and evidence."
        }

        val weaknessSummary = when {
            score.totalScore < 60 -> "Add more concrete examples, tradeoffs, and a clearer explanation of why your approach works."
            tradeoffScore.toInt() < 60 -> "The answer needs a stronger explanation of tradeoffs, constraints, and failure handling."
            else -> "Push deeper on examples and measurable outcomes to make the answer more interview-ready."
        }

        val recommendedNextStep = when {
            attempt.answerMode == "skip" || attempt.answerMode == "unanswered" ->
                "Write a full first-pass answer before optimizing for depth."
            score.totalScore < 60 ->
                "Re-answer using a concrete production example with metrics, constraints, and tradeoffs."
            else ->
                "Practice one likely follow-up question and make the tradeoff discussion more explicit."
        }

        return AnswerAnalysisEntity(
            answerAttemptId = attempt.id,
            overallScore = weighted(score.totalScore.toDouble()),
            depthScore = depthScore,
            clarityScore = clarityScore,
            accuracyScore = accuracyScore,
            exampleScore = exampleScore,
            tradeoffScore = tradeoffScore,
            confidenceScore = confidenceScore,
            strengthSummary = strengthSummary,
            weaknessSummary = weaknessSummary,
            recommendedNextStep = recommendedNextStep,
            createdAt = now,
        )
    }

    private fun weighted(value: Double): BigDecimal = value.coerceIn(0.0, 100.0).toBigDecimal().setScale(2, RoundingMode.HALF_UP)

    private companion object {
        val TRADEOFF_HINTS = listOf("tradeoff", "however", "instead", "versus", "vs", "cost")
        val EXAMPLE_HINTS = listOf("for example", "for instance", "because", "measured", "latency", "throughput")
    }
}
