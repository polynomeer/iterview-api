package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionRoleEntity
import com.example.interviewplatform.question.entity.QuestionRoleId
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionRoleRepository : JpaRepository<QuestionRoleEntity, QuestionRoleId> {
    fun findByIdQuestionIdIn(questionIds: List<Long>): List<QuestionRoleEntity>
}
