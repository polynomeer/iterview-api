package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.UserQuestionReferenceAnswerEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserQuestionReferenceAnswerRepository : JpaRepository<UserQuestionReferenceAnswerEntity, Long> {
    fun findByQuestionIdAndUserIdOrderByDisplayOrderAscIdAsc(questionId: Long, userId: Long): List<UserQuestionReferenceAnswerEntity>

    fun countByQuestionIdAndUserId(questionId: Long, userId: Long): Long
}
