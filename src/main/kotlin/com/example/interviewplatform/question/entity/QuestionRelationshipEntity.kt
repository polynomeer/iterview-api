package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "question_relationships")
class QuestionRelationshipEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "parent_question_id", nullable = false)
    val parentQuestionId: Long,
    @Column(name = "child_question_id", nullable = false)
    val childQuestionId: Long,
    @Column(name = "relationship_type", nullable = false)
    val relationshipType: String,
    @Column(nullable = false)
    val depth: Int,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
