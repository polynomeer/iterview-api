package com.example.interviewplatform.skill.repository

import com.example.interviewplatform.skill.entity.SkillEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SkillRepository : JpaRepository<SkillEntity, Long> {
    fun findByNameIn(names: List<String>): List<SkillEntity>
}
