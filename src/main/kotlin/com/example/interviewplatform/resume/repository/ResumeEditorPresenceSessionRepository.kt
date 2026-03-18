package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEditorPresenceSessionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeEditorPresenceSessionRepository : JpaRepository<ResumeEditorPresenceSessionEntity, Long> {
    fun findByResumeEditorWorkspaceIdAndSessionKey(
        resumeEditorWorkspaceId: Long,
        sessionKey: String,
    ): ResumeEditorPresenceSessionEntity?

    fun findByResumeEditorWorkspaceIdOrderByUpdatedAtDesc(resumeEditorWorkspaceId: Long): List<ResumeEditorPresenceSessionEntity>
}
