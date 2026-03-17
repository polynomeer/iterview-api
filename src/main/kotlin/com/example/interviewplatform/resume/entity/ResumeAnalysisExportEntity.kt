package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_analysis_exports")
class ResumeAnalysisExportEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "resume_analysis_id", nullable = false)
    val resumeAnalysisId: Long,
    @Column(name = "export_type", nullable = false)
    val exportType: String,
    @Column(name = "format_type")
    val formatType: String? = null,
    @Column(name = "file_name", nullable = false)
    val fileName: String,
    @Column(name = "storage_key", nullable = false)
    val storageKey: String,
    @Column(name = "file_size_bytes", nullable = false)
    val fileSizeBytes: Long,
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    val checksumSha256: String,
    @Column(name = "page_count")
    val pageCount: Int? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
