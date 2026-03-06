package com.example.interviewplatform.review.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "review_queue")
class ReviewQueueEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "trigger_answer_attempt_id", nullable = false)
    val triggerAnswerAttemptId: Long,
    @Column(name = "reason_type", nullable = false)
    val reasonType: String,
    @Column(nullable = false)
    val priority: Int,
    @Column(name = "scheduled_for", nullable = false)
    val scheduledFor: Instant,
    @Column(nullable = false)
    val status: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
