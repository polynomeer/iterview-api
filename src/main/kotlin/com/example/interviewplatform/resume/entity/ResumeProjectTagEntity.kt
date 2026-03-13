package com.example.interviewplatform.resume.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "resume_project_tags")
class ResumeProjectTagEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_project_snapshot_id", nullable = false)
    val resumeProjectSnapshotId: Long,
    @Column(name = "tag_name", nullable = false)
    val tagName: String,
    @Column(name = "tag_type")
    val tagType: String? = null,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "source_text")
    val sourceText: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
