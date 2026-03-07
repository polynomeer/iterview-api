package com.example.interviewplatform.answer.repository

import com.example.interviewplatform.answer.entity.AnswerFeedbackItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AnswerFeedbackItemRepository : JpaRepository<AnswerFeedbackItemEntity, Long> {
    fun findByAnswerAttemptIdOrderByDisplayOrderAsc(answerAttemptId: Long): List<AnswerFeedbackItemEntity>

    fun findByAnswerAttemptIdInOrderByAnswerAttemptIdAscDisplayOrderAsc(answerAttemptIds: List<Long>): List<AnswerFeedbackItemEntity>
}
