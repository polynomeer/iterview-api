package com.example.interviewplatform.jobposting.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "job_postings")
class JobPostingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "input_type", nullable = false)
    val inputType: String,
    @Column(name = "source_url")
    val sourceUrl: String? = null,
    @Column(name = "raw_text")
    val rawText: String? = null,
    @Column(name = "company_name")
    val companyName: String? = null,
    @Column(name = "role_name")
    val roleName: String? = null,
    @Column(name = "parsed_requirements_json", nullable = false)
    val parsedRequirementsJson: String,
    @Column(name = "parsed_nice_to_have_json", nullable = false)
    val parsedNiceToHaveJson: String,
    @Column(name = "parsed_keywords_json", nullable = false)
    val parsedKeywordsJson: String,
    @Column(name = "parsed_responsibilities_json", nullable = false)
    val parsedResponsibilitiesJson: String,
    @Column(name = "parsed_summary")
    val parsedSummary: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
