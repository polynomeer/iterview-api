package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_achievement_items")
class ResumeAchievementItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "resume_experience_snapshot_id")
    val resumeExperienceSnapshotId: Long? = null,
    @Column(name = "resume_project_snapshot_id")
    val resumeProjectSnapshotId: Long? = null,
    @Column(nullable = false)
    val title: String,
    @Column(name = "metric_text")
    val metricText: String? = null,
    @Column(name = "impact_summary", nullable = false)
    val impactSummary: String,
    @Column(name = "source_text")
    val sourceText: String? = null,
    @Column(name = "severity_hint")
    val severityHint: String? = null,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
