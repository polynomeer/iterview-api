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
@Table(name = "resume_certification_items")
class ResumeCertificationItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(nullable = false)
    val name: String,
    @Column(name = "issuer_name")
    val issuerName: String? = null,
    @Column(name = "credential_code")
    val credentialCode: String? = null,
    @Column(name = "issued_on")
    val issuedOn: LocalDate? = null,
    @Column(name = "expires_on")
    val expiresOn: LocalDate? = null,
    @Column(name = "score_text")
    val scoreText: String? = null,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "source_text")
    val sourceText: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
