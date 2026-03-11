package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeExperienceSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeExperienceSnapshotRepository : JpaRepository<ResumeExperienceSnapshotEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeExperienceSnapshotEntity>
}
