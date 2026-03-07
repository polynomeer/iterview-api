package com.example.interviewplatform.answer.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "answer_feedback_items")
class AnswerFeedbackItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "answer_attempt_id", nullable = false)
    val answerAttemptId: Long,
    @Column(name = "feedback_type", nullable = false)
    val feedbackType: String,
    @Column(name = "severity", nullable = false)
    val severity: String,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false)
    val body: String,
    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
