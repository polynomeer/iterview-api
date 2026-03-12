package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEducationItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeEducationItemRepository : JpaRepository<ResumeEducationItemEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeEducationItemEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
