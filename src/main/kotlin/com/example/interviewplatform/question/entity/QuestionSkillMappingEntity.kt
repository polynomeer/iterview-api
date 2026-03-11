package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "question_skill_mappings")
class QuestionSkillMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "skill_id", nullable = false)
    val skillId: Long,
    @Column(nullable = false)
    val weight: BigDecimal,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
