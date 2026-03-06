package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "question_roles")
class QuestionRoleEntity(
    @EmbeddedId
    val id: QuestionRoleId,
    @Column(name = "relevance_score", nullable = false)
    val relevanceScore: BigDecimal,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
