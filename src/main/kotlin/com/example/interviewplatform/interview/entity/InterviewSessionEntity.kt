package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interview_sessions")
class InterviewSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "resume_version_id")
    val resumeVersionId: Long? = null,
    @Column(name = "source_interview_record_id")
    val sourceInterviewRecordId: Long? = null,
    @Column(name = "session_type", nullable = false)
    val sessionType: String,
    @Column(name = "replay_mode")
    val replayMode: String? = null,
    @Column(name = "interview_mode", nullable = false)
    val interviewMode: String,
    @Column(nullable = false)
    val status: String,
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,
    @Column(name = "ended_at")
    val endedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
