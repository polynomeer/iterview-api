package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeQuestionHeatmapLinkEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeQuestionHeatmapLinkRepository : JpaRepository<ResumeQuestionHeatmapLinkEntity, Long> {
    fun findByResumeVersionIdAndActiveTrue(resumeVersionId: Long): List<ResumeQuestionHeatmapLinkEntity>

    fun findByIdAndResumeVersionId(id: Long, resumeVersionId: Long): ResumeQuestionHeatmapLinkEntity?

    fun findByInterviewRecordQuestionId(interviewRecordQuestionId: Long): ResumeQuestionHeatmapLinkEntity?
}
