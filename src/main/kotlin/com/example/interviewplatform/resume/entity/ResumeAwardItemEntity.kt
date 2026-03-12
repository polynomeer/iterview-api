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
@Table(name = "resume_award_items")
class ResumeAwardItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(name = "issuer_name")
    val issuerName: String? = null,
    @Column(name = "awarded_on")
    val awardedOn: LocalDate? = null,
    @Column(name = "description")
    val description: String? = null,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "source_text")
    val sourceText: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
