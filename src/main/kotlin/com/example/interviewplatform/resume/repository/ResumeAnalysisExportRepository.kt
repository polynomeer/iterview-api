package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeAnalysisExportEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeAnalysisExportRepository : JpaRepository<ResumeAnalysisExportEntity, Long> {
    fun findByResumeAnalysisIdOrderByCreatedAtDescIdDesc(resumeAnalysisId: Long): List<ResumeAnalysisExportEntity>

    fun findByIdAndResumeAnalysisId(id: Long, resumeAnalysisId: Long): ResumeAnalysisExportEntity?
}
