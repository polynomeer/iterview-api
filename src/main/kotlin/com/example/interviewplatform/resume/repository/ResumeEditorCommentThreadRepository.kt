package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEditorCommentThreadEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeEditorCommentThreadRepository : JpaRepository<ResumeEditorCommentThreadEntity, Long> {
    fun findByResumeEditorWorkspaceIdOrderByCreatedAtAsc(resumeEditorWorkspaceId: Long): List<ResumeEditorCommentThreadEntity>
    fun findByIdAndResumeVersionId(id: Long, resumeVersionId: Long): ResumeEditorCommentThreadEntity?
}
