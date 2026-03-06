package com.example.interviewplatform.question.service

import com.example.interviewplatform.question.dto.QuestionSummaryDto
import com.example.interviewplatform.question.repository.QuestionRepository
import org.springframework.stereotype.Service

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
) {
    fun listActiveQuestions(): List<QuestionSummaryDto> =
        questionRepository.findByIsActiveTrue().map {
            QuestionSummaryDto(it.id, it.title, it.difficultyLevel)
        }
}
