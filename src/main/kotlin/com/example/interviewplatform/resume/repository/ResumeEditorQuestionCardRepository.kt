package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEditorQuestionCardEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeEditorQuestionCardRepository : JpaRepository<ResumeEditorQuestionCardEntity, Long> {
    fun findByResumeEditorWorkspaceIdOrderByCreatedAtAsc(resumeEditorWorkspaceId: Long): List<ResumeEditorQuestionCardEntity>
    fun findByIdAndResumeVersionId(id: Long, resumeVersionId: Long): ResumeEditorQuestionCardEntity?
}
