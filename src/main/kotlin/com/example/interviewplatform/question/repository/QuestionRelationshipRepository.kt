package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionRelationshipEntity
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionRelationshipRepository : JpaRepository<QuestionRelationshipEntity, Long> {
    fun findByParentQuestionIdOrderByDisplayOrderAscIdAsc(parentQuestionId: Long): List<QuestionRelationshipEntity>

    fun findByParentQuestionIdInOrderByParentQuestionIdAscDisplayOrderAscIdAsc(parentQuestionIds: List<Long>): List<QuestionRelationshipEntity>
}
