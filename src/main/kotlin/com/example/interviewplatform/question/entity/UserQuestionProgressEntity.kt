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
@Table(name = "user_question_progress")
class UserQuestionProgressEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "latest_answer_attempt_id")
    val latestAnswerAttemptId: Long? = null,
    @Column(name = "best_answer_attempt_id")
    val bestAnswerAttemptId: Long? = null,
    @Column(name = "latest_score")
    val latestScore: BigDecimal? = null,
    @Column(name = "best_score")
    val bestScore: BigDecimal? = null,
    @Column(name = "total_attempt_count", nullable = false)
    val totalAttemptCount: Int,
    @Column(name = "unanswered_count", nullable = false)
    val unansweredCount: Int,
    @Column(name = "current_status", nullable = false)
    val currentStatus: String,
    @Column(name = "archived_at")
    val archivedAt: Instant? = null,
    @Column(name = "last_answered_at")
    val lastAnsweredAt: Instant? = null,
    @Column(name = "next_review_at")
    val nextReviewAt: Instant? = null,
    @Column(name = "mastery_level")
    val masteryLevel: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
