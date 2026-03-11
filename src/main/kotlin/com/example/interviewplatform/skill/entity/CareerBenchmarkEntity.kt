package com.example.interviewplatform.skill.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "career_benchmarks")
class CareerBenchmarkEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "job_role_id", nullable = false)
    val jobRoleId: Long,
    @Column(name = "experience_band_code", nullable = false)
    val experienceBandCode: String,
    @Column(name = "skill_category_id", nullable = false)
    val skillCategoryId: Long,
    @Column(name = "benchmark_score", nullable = false)
    val benchmarkScore: BigDecimal,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
