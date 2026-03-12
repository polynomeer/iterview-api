package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionReferenceAnswerEntity
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionReferenceAnswerRepository : JpaRepository<QuestionReferenceAnswerEntity, Long> {
    fun findByQuestionIdOrderByDisplayOrderAscIdAsc(questionId: Long): List<QuestionReferenceAnswerEntity>
    fun findByQuestionIdInOrderByQuestionIdAscDisplayOrderAscIdAsc(questionIds: List<Long>): List<QuestionReferenceAnswerEntity>
}
