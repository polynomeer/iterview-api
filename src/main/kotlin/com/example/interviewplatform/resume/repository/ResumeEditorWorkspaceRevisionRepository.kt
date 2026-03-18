package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEditorWorkspaceRevisionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeEditorWorkspaceRevisionRepository : JpaRepository<ResumeEditorWorkspaceRevisionEntity, Long> {
    fun findByResumeEditorWorkspaceIdOrderByRevisionNoDesc(resumeEditorWorkspaceId: Long): List<ResumeEditorWorkspaceRevisionEntity>
    fun findByIdAndResumeVersionId(id: Long, resumeVersionId: Long): ResumeEditorWorkspaceRevisionEntity?
    fun findByResumeEditorWorkspaceIdAndRevisionNo(
        resumeEditorWorkspaceId: Long,
        revisionNo: Int,
    ): ResumeEditorWorkspaceRevisionEntity?
}
