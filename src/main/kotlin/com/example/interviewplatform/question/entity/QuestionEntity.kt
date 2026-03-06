package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "questions")
class QuestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "author_user_id")
    val authorUserId: Long? = null,
    @Column(name = "category_id", nullable = false)
    val categoryId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false)
    val body: String,
    @Column(name = "question_type", nullable = false)
    val questionType: String,
    @Column(name = "difficulty_level", nullable = false)
    val difficultyLevel: String,
    @Column(name = "source_type", nullable = false)
    val sourceType: String,
    @Column(name = "quality_status", nullable = false)
    val qualityStatus: String,
    @Column(nullable = false)
    val visibility: String,
    @Column(name = "expected_answer_seconds")
    val expectedAnswerSeconds: Int? = null,
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
