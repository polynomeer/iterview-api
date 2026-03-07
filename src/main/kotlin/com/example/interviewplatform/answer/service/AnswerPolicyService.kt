package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import org.springframework.stereotype.Service

@Service
class AnswerPolicyService {
    fun evaluate(
        score: ScoreSummaryDto,
        attemptCount: Int,
        answerMode: String,
    ): AnswerPolicyDecision {
        val normalizedMode = answerMode.trim().lowercase()
        if (normalizedMode == MODE_SKIP || normalizedMode == MODE_UNANSWERED) {
            return AnswerPolicyDecision(
                archive = false,
                needsRetry = true,
                retryReasonType = REASON_SKIPPED_OR_UNANSWERED,
                retryPriority = PRIORITY_HIGH,
                retryDelayDays = 1L,
                weakDimensions = listOf(MODE_SKIP),
            )
        }

        val weakDimensions = collectWeakDimensions(score)
        val lowTotal = score.totalScore < PASS_TOTAL_THRESHOLD
        val needsRetry = lowTotal || weakDimensions.isNotEmpty()

        if (!needsRetry) {
            val archive = shouldArchive(score, attemptCount)
            return AnswerPolicyDecision(
                archive = archive,
                needsRetry = false,
                retryReasonType = null,
                retryPriority = null,
                retryDelayDays = null,
                weakDimensions = weakDimensions,
            )
        }

        val reason = if (lowTotal) REASON_LOW_TOTAL else REASON_WEAK_DIMENSION
        val severeWeakDimension = weakDimensions.any { dimension ->
            dimensionScore(dimension, score) < SEVERE_DIMENSION_THRESHOLD
        }

        val priority = when {
            score.totalScore < VERY_LOW_TOTAL_THRESHOLD || severeWeakDimension -> PRIORITY_HIGH
            score.totalScore < PASS_TOTAL_THRESHOLD -> PRIORITY_MEDIUM
            else -> PRIORITY_LOW
        }
        val delayDays = when (priority) {
            PRIORITY_HIGH -> 1L
            PRIORITY_MEDIUM -> 2L
            else -> 3L
        }

        return AnswerPolicyDecision(
            archive = false,
            needsRetry = true,
            retryReasonType = reason,
            retryPriority = priority,
            retryDelayDays = delayDays,
            weakDimensions = weakDimensions,
        )
    }

    private fun shouldArchive(score: ScoreSummaryDto, attemptCount: Int): Boolean {
        if (attemptCount < ARCHIVE_MIN_ATTEMPTS || score.totalScore < ARCHIVE_TOTAL_THRESHOLD) {
            return false
        }

        return CORE_ARCHIVE_DIMENSIONS.all { dimension ->
            dimensionScore(dimension, score) >= ARCHIVE_DIMENSION_THRESHOLD
        }
    }

    private fun collectWeakDimensions(score: ScoreSummaryDto): List<String> = DIMENSION_ORDER.filter { dimension ->
        dimensionScore(dimension, score) < WEAK_DIMENSION_THRESHOLD
    }

    private fun dimensionScore(dimension: String, score: ScoreSummaryDto): Int = when (dimension) {
        DIM_STRUCTURE -> score.structureScore
        DIM_SPECIFICITY -> score.specificityScore
        DIM_TECHNICAL_ACCURACY -> score.technicalAccuracyScore
        DIM_ROLE_FIT -> score.roleFitScore
        DIM_COMPANY_FIT -> score.companyFitScore
        DIM_COMMUNICATION -> score.communicationScore
        else -> 0
    }

    private companion object {
        const val PASS_TOTAL_THRESHOLD = 60
        const val VERY_LOW_TOTAL_THRESHOLD = 45
        const val WEAK_DIMENSION_THRESHOLD = 50
        const val SEVERE_DIMENSION_THRESHOLD = 35

        const val ARCHIVE_TOTAL_THRESHOLD = 85
        const val ARCHIVE_MIN_ATTEMPTS = 2
        const val ARCHIVE_DIMENSION_THRESHOLD = 70

        const val PRIORITY_HIGH = 100
        const val PRIORITY_MEDIUM = 80
        const val PRIORITY_LOW = 60

        const val REASON_LOW_TOTAL = "low_total"
        const val REASON_WEAK_DIMENSION = "weak_dimension"
        const val REASON_SKIPPED_OR_UNANSWERED = "skipped_or_unanswered"

        const val MODE_SKIP = "skip"
        const val MODE_UNANSWERED = "unanswered"

        const val DIM_STRUCTURE = "structure"
        const val DIM_SPECIFICITY = "specificity"
        const val DIM_TECHNICAL_ACCURACY = "technicalAccuracy"
        const val DIM_ROLE_FIT = "roleFit"
        const val DIM_COMPANY_FIT = "companyFit"
        const val DIM_COMMUNICATION = "communication"

        val DIMENSION_ORDER = listOf(
            DIM_STRUCTURE,
            DIM_SPECIFICITY,
            DIM_TECHNICAL_ACCURACY,
            DIM_ROLE_FIT,
            DIM_COMPANY_FIT,
            DIM_COMMUNICATION,
        )

        val CORE_ARCHIVE_DIMENSIONS = listOf(
            DIM_STRUCTURE,
            DIM_SPECIFICITY,
            DIM_TECHNICAL_ACCURACY,
            DIM_COMMUNICATION,
        )
    }
}
