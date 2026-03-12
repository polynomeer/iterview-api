package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeAchievementItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeAchievementItemRepository : JpaRepository<ResumeAchievementItemEntity, Long> {
    fun findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId: Long): List<ResumeAchievementItemEntity>

    fun deleteByResumeVersionId(resumeVersionId: Long)
}
