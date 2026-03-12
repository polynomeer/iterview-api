package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_profile_snapshots")
class ResumeProfileSnapshotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "full_name")
    val fullName: String? = null,
    @Column(name = "headline")
    val headline: String? = null,
    @Column(name = "summary_text")
    val summaryText: String? = null,
    @Column(name = "location_text")
    val locationText: String? = null,
    @Column(name = "years_of_experience_text")
    val yearsOfExperienceText: String? = null,
    @Column(name = "source_text")
    val sourceText: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
