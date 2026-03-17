package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeAnalysisEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeAnalysisRepository : JpaRepository<ResumeAnalysisEntity, Long> {
    fun findByResumeVersionIdOrderByCreatedAtDesc(versionId: Long): List<ResumeAnalysisEntity>

    fun findByIdAndUserId(id: Long, userId: Long): ResumeAnalysisEntity?
}
