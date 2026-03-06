package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionRepository : JpaRepository<QuestionEntity, Long> {
    fun findByIsActiveTrue(): List<QuestionEntity>
}
