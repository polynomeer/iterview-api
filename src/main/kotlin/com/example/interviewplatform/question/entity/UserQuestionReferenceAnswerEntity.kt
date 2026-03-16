package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_question_reference_answers")
class UserQuestionReferenceAnswerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(name = "answer_text", nullable = false)
    val answerText: String,
    @Column(name = "answer_format", nullable = false)
    val answerFormat: String,
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
