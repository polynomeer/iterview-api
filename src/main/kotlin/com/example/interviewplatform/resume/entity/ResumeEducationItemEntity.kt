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
@Table(name = "resume_education_items")
class ResumeEducationItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "resume_version_id", nullable = false)
    val resumeVersionId: Long,
    @Column(name = "institution_name", nullable = false)
    val institutionName: String,
    @Column(name = "degree_name")
    val degreeName: String? = null,
    @Column(name = "field_of_study")
    val fieldOfStudy: String? = null,
    @Column(name = "started_on")
    val startedOn: LocalDate? = null,
    @Column(name = "ended_on")
    val endedOn: LocalDate? = null,
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
