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
@Table(name = "skill_category_scores")
class SkillCategoryScoreEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "skill_category_id", nullable = false)
    val skillCategoryId: Long,
    @Column(nullable = false)
    val score: BigDecimal,
    @Column(name = "answered_question_count", nullable = false)
    val answeredQuestionCount: Int,
    @Column(name = "weak_question_count", nullable = false)
    val weakQuestionCount: Int,
    @Column(name = "benchmark_score")
    val benchmarkScore: BigDecimal? = null,
    @Column(name = "gap_score")
    val gapScore: BigDecimal? = null,
    @Column(name = "calculated_at", nullable = false)
    val calculatedAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
