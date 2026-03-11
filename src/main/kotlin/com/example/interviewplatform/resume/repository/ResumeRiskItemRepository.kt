package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeRiskItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeRiskItemRepository : JpaRepository<ResumeRiskItemEntity, Long> {
    fun findByResumeVersionIdOrderBySeverityDescIdAsc(resumeVersionId: Long): List<ResumeRiskItemEntity>
}
