package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_editor_presence_sessions")
class ResumeEditorPresenceSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_editor_workspace_id", nullable = false)
    val resumeEditorWorkspaceId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "session_key", nullable = false)
    val sessionKey: String,
    @Column(name = "view_mode")
    val viewMode: String? = null,
    @Column(name = "selected_block_id")
    val selectedBlockId: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
