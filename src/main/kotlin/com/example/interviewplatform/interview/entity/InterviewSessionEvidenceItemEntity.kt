package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interview_session_evidence_items")
class InterviewSessionEvidenceItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "interview_session_id", nullable = false)
    val interviewSessionId: Long,
    @Column(nullable = false)
    val section: String,
    @Column
    val label: String? = null,
    @Column(nullable = false)
    val snippet: String,
    @Column(nullable = false)
    val facet: String,
    @Column(name = "source_record_type", nullable = false)
    val sourceRecordType: String,
    @Column(name = "source_record_id", nullable = false)
    val sourceRecordId: Long,
    @Column(name = "coverage_status", nullable = false)
    val coverageStatus: String,
    @Column(name = "coverage_priority", nullable = false)
    val coveragePriority: Int = 0,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
