package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.UserQuestionLearningMaterialEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserQuestionLearningMaterialRepository : JpaRepository<UserQuestionLearningMaterialEntity, Long> {
    fun findByQuestionIdAndUserIdOrderByDisplayOrderAscIdAsc(questionId: Long, userId: Long): List<UserQuestionLearningMaterialEntity>

    fun countByQuestionIdAndUserId(questionId: Long, userId: Long): Long
}
