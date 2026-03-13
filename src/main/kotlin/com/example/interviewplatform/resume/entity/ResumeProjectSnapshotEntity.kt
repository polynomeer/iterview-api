package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "resume_project_snapshots")
class ResumeProjectSnapshotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "resume_experience_snapshot_id")
    val resumeExperienceSnapshotId: Long? = null,
    @Column(nullable = false)
    val title: String,
    @Column(name = "organization_name")
    val organizationName: String? = null,
    @Column(name = "role_name")
    val roleName: String? = null,
    @Column(name = "summary_text", nullable = false)
    val summaryText: String,
    @Column(name = "content_text")
    val contentText: String? = null,
    @Column(name = "project_category_code")
    val projectCategoryCode: String? = null,
    @Column(name = "project_category_name")
    val projectCategoryName: String? = null,
    @Column(name = "tech_stack_text")
    val techStackText: String? = null,
    @Column(name = "started_on")
    val startedOn: LocalDate? = null,
    @Column(name = "ended_on")
    val endedOn: LocalDate? = null,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "source_text")
    val sourceText: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
