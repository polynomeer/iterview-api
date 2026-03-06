package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionTagEntity
import com.example.interviewplatform.question.entity.QuestionTagId
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionTagRepository : JpaRepository<QuestionTagEntity, QuestionTagId> {
    fun findByIdQuestionIdIn(questionIds: List<Long>): List<QuestionTagEntity>
}
