package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_risk_items")
class ResumeRiskItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "resume_experience_snapshot_id")
    val resumeExperienceSnapshotId: Long? = null,
    @Column(name = "linked_question_id")
    val linkedQuestionId: Long? = null,
    @Column(name = "risk_type", nullable = false)
    val riskType: String,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false)
    val description: String,
    @Column(nullable = false)
    val severity: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
