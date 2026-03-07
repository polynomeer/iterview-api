package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.UserQuestionProgressEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserQuestionProgressRepository : JpaRepository<UserQuestionProgressEntity, Long> {
    fun findByUserIdAndQuestionId(userId: Long, questionId: Long): UserQuestionProgressEntity?

    fun findByUserIdAndQuestionIdIn(userId: Long, questionIds: List<Long>): List<UserQuestionProgressEntity>

    fun findByUserIdAndCurrentStatusOrderByArchivedAtDesc(userId: Long, currentStatus: String): List<UserQuestionProgressEntity>
}
