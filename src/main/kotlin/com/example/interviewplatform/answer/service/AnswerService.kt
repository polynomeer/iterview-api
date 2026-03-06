package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import com.example.interviewplatform.answer.repository.AnswerAttemptRepository
import org.springframework.stereotype.Service

@Service
class AnswerService(
    private val answerAttemptRepository: AnswerAttemptRepository,
    private val scoringService: ScoringService,
) {
    fun evaluateAnswer(answerText: String): ScoreSummaryDto = scoringService.score(answerText)

    fun countAttempts(userId: Long, questionId: Long): Int =
        answerAttemptRepository.findByUserIdAndQuestionIdOrderBySubmittedAtDesc(userId, questionId).size
}
