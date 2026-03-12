package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeProfileSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeProfileSnapshotRepository : JpaRepository<ResumeProfileSnapshotEntity, Long> {
    fun findByResumeVersionId(resumeVersionId: Long): ResumeProfileSnapshotEntity?

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
