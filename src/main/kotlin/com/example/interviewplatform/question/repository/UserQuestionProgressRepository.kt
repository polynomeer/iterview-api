package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.UserQuestionProgressEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserQuestionProgressRepository : JpaRepository<UserQuestionProgressEntity, Long> {
    fun findByUserIdAndQuestionId(userId: Long, questionId: Long): UserQuestionProgressEntity?
}
