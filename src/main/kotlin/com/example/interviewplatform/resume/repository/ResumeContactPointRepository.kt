package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeContactPointEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeContactPointRepository : JpaRepository<ResumeContactPointEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeContactPointEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
