package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeAwardItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeAwardItemRepository : JpaRepository<ResumeAwardItemEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeAwardItemEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
