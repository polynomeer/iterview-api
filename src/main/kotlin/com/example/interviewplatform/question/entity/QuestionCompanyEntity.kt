package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "question_companies")
class QuestionCompanyEntity(
    @EmbeddedId
    val id: QuestionCompanyId,
    @Column(name = "relevance_score", nullable = false)
    val relevanceScore: BigDecimal,
    @Column(name = "is_past_frequent", nullable = false)
    val isPastFrequent: Boolean,
    @Column(name = "is_trending_recent", nullable = false)
    val isTrendingRecent: Boolean,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
