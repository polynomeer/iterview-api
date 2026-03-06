package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_versions")
class ResumeVersionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_id", nullable = false)
    val resumeId: Long,
    @Column(name = "version_no", nullable = false)
    val versionNo: Int,
    @Column(name = "file_url")
    val fileUrl: String? = null,
    @Column(name = "raw_text")
    val rawText: String? = null,
    @Column(name = "parsed_json", columnDefinition = "TEXT")
    val parsedJson: String? = null,
    @Column(name = "summary_text")
    val summaryText: String? = null,
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = false,
    @Column(name = "uploaded_at", nullable = false)
    val uploadedAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
