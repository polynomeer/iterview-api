package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionLearningMaterialEntity
import com.example.interviewplatform.question.entity.QuestionLearningMaterialId
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionLearningMaterialRepository : JpaRepository<QuestionLearningMaterialEntity, QuestionLearningMaterialId> {
    fun findByIdQuestionIdIn(questionIds: List<Long>): List<QuestionLearningMaterialEntity>
}
