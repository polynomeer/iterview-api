package com.example.interviewplatform.answer.repository

import com.example.interviewplatform.answer.entity.AnswerAnalysisEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AnswerAnalysisRepository : JpaRepository<AnswerAnalysisEntity, Long> {
    fun findByAnswerAttemptId(answerAttemptId: Long): AnswerAnalysisEntity?

    fun findByAnswerAttemptIdIn(answerAttemptIds: List<Long>): List<AnswerAnalysisEntity>
}
