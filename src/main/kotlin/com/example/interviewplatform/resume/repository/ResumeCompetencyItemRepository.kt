package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeCompetencyItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeCompetencyItemRepository : JpaRepository<ResumeCompetencyItemEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeCompetencyItemEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
