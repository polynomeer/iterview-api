package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeSkillSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeSkillSnapshotRepository : JpaRepository<ResumeSkillSnapshotEntity, Long> {
    fun findByResumeVersionIdOrderByIdAsc(resumeVersionId: Long): List<ResumeSkillSnapshotEntity>
    fun deleteByResumeVersionId(resumeVersionId: Long)
}
