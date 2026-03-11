package com.example.interviewplatform.skill.repository

import com.example.interviewplatform.skill.entity.SkillCategoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SkillCategoryRepository : JpaRepository<SkillCategoryEntity, Long> {
    fun findByCode(code: String): SkillCategoryEntity?
}
