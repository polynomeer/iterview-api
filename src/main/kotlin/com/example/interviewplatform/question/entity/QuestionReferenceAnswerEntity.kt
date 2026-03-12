package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "question_reference_answers")
class QuestionReferenceAnswerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(name = "answer_text", nullable = false)
    val answerText: String,
    @Column(name = "answer_format", nullable = false)
    val answerFormat: String,
    @Column(name = "source_type", nullable = false)
    val sourceType: String,
    @Column(name = "target_role_id")
    val targetRoleId: Long? = null,
    @Column(name = "company_id")
    val companyId: Long? = null,
    @Column(name = "is_official", nullable = false)
    val isOfficial: Boolean = false,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
