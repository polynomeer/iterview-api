package com.example.interviewplatform.skill.repository

import com.example.interviewplatform.skill.entity.SkillCategoryScoreEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SkillCategoryScoreRepository : JpaRepository<SkillCategoryScoreEntity, Long> {
    fun findByUserIdOrderByScoreDesc(userId: Long): List<SkillCategoryScoreEntity>

    fun findByUserIdAndSkillCategoryId(userId: Long, skillCategoryId: Long): SkillCategoryScoreEntity?
}
