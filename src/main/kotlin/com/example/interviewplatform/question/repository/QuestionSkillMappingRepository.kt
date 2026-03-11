package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.QuestionSkillMappingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface QuestionSkillMappingRepository : JpaRepository<QuestionSkillMappingEntity, Long> {
    fun findByQuestionIdIn(questionIds: List<Long>): List<QuestionSkillMappingEntity>

    fun findBySkillIdIn(skillIds: List<Long>): List<QuestionSkillMappingEntity>
}
