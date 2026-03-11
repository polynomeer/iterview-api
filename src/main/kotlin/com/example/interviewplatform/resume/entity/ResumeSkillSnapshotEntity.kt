package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "resume_skill_snapshots")
class ResumeSkillSnapshotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "skill_id")
    val skillId: Long? = null,
    @Column(name = "skill_name", nullable = false)
    val skillName: String,
    @Column(name = "source_text")
    val sourceText: String? = null,
    @Column(name = "confidence_score")
    val confidenceScore: BigDecimal? = null,
    @Column(name = "is_confirmed", nullable = false)
    val isConfirmed: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
