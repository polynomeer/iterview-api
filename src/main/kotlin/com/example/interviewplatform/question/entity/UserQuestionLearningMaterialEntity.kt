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
@Table(name = "user_question_learning_materials")
class UserQuestionLearningMaterialEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(name = "material_type", nullable = false)
    val materialType: String,
    @Column(name = "description")
    val description: String? = null,
    @Column(name = "content_text")
    val contentText: String? = null,
    @Column(name = "content_url")
    val contentUrl: String? = null,
    @Column(name = "source_name")
    val sourceName: String? = null,
    @Column(name = "difficulty_level")
    val difficultyLevel: String? = null,
    @Column(name = "estimated_minutes")
    val estimatedMinutes: Int? = null,
    @Column(name = "relationship_type")
    val relationshipType: String? = null,
    @Column(name = "label_override")
    val labelOverride: String? = null,
    @Column(name = "relevance_score")
    val relevanceScore: BigDecimal? = null,
    @Column(name = "source_type", nullable = false)
    val sourceType: String,
    @Column(name = "content_locale")
    val contentLocale: String? = null,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
