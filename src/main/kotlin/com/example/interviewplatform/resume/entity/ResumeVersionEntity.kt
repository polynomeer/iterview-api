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
    @Column(name = "file_name")
    val fileName: String? = null,
    @Column(name = "file_type")
    val fileType: String? = null,
    @Column(name = "storage_key")
    val storageKey: String? = null,
    @Column(name = "file_size_bytes")
    val fileSizeBytes: Long? = null,
    @Column(name = "checksum_sha256", length = 64)
    val checksumSha256: String? = null,
    @Column(name = "raw_text")
    val rawText: String? = null,
    @Column(name = "parsed_json", columnDefinition = "TEXT")
    val parsedJson: String? = null,
    @Column(name = "summary_text")
    val summaryText: String? = null,
    @Column(name = "parsing_status", nullable = false)
    val parsingStatus: String,
    @Column(name = "parse_started_at")
    val parseStartedAt: Instant? = null,
    @Column(name = "parse_completed_at")
    val parseCompletedAt: Instant? = null,
    @Column(name = "parse_error_message")
    val parseErrorMessage: String? = null,
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = false,
    @Column(name = "uploaded_at", nullable = false)
    val uploadedAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
