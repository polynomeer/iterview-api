package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeCertificationItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeCertificationItemRepository : JpaRepository<ResumeCertificationItemEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeCertificationItemEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
