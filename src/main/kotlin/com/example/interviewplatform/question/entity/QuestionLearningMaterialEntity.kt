package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "question_learning_materials")
class QuestionLearningMaterialEntity(
    @EmbeddedId
    val id: QuestionLearningMaterialId,
    @Column(name = "relevance_score", nullable = false)
    val relevanceScore: BigDecimal,
    @Column(name = "display_order")
    val displayOrder: Int? = null,
    @Column(name = "relationship_type")
    val relationshipType: String? = null,
    @Column(name = "label_override")
    val labelOverride: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
