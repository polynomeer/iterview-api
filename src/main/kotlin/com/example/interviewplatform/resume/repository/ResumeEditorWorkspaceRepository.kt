package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEditorWorkspaceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeEditorWorkspaceRepository : JpaRepository<ResumeEditorWorkspaceEntity, Long> {
    fun findByResumeVersionId(resumeVersionId: Long): ResumeEditorWorkspaceEntity?
}
