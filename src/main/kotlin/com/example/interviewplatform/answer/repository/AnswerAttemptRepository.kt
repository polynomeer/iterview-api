package com.example.interviewplatform.answer.repository

import com.example.interviewplatform.answer.entity.AnswerAttemptEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AnswerAttemptRepository : JpaRepository<AnswerAttemptEntity, Long> {
    fun findByUserIdAndQuestionIdOrderBySubmittedAtDesc(userId: Long, questionId: Long): List<AnswerAttemptEntity>

    fun findByUserIdAndQuestionIdOrderByAttemptNoDesc(userId: Long, questionId: Long): List<AnswerAttemptEntity>

    fun findTopByUserIdAndQuestionIdOrderByAttemptNoDesc(userId: Long, questionId: Long): AnswerAttemptEntity?

    fun findByIdAndUserId(id: Long, userId: Long): AnswerAttemptEntity?
}
