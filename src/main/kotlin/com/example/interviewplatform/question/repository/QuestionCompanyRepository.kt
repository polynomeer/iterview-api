package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionCompanyEntity
import com.example.interviewplatform.question.entity.QuestionCompanyId
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionCompanyRepository : JpaRepository<QuestionCompanyEntity, QuestionCompanyId> {
    fun findByIdQuestionIdIn(questionIds: List<Long>): List<QuestionCompanyEntity>
}
