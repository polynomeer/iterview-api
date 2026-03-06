package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import org.springframework.stereotype.Service

@Service
class ScoringService {
    fun score(answerText: String): ScoreSummaryDto {
        val normalizedLength = answerText.trim().length
        val totalScore = when {
            normalizedLength >= 500 -> 85
            normalizedLength >= 250 -> 70
            normalizedLength >= 100 -> 55
            else -> 40
        }
        val evaluationResult = if (totalScore >= 60) "PASS" else "FAIL"
        return ScoreSummaryDto(totalScore, evaluationResult)
    }
}
