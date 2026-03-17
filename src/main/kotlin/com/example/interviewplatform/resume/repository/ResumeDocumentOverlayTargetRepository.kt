package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeDocumentOverlayTargetEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeDocumentOverlayTargetRepository : JpaRepository<ResumeDocumentOverlayTargetEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeDocumentOverlayTargetEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
