package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeAnalysisSuggestionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeAnalysisSuggestionRepository : JpaRepository<ResumeAnalysisSuggestionEntity, Long> {
    fun findByResumeAnalysisIdOrderByDisplayOrderAscIdAsc(resumeAnalysisId: Long): List<ResumeAnalysisSuggestionEntity>

    fun findByIdAndResumeAnalysisId(id: Long, resumeAnalysisId: Long): ResumeAnalysisSuggestionEntity?
}
