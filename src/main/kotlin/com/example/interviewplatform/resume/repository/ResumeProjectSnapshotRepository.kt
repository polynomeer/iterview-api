package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeProjectSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeProjectSnapshotRepository : JpaRepository<ResumeProjectSnapshotEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeProjectSnapshotEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
