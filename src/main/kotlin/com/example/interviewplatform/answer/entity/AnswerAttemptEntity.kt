package com.example.interviewplatform.answer.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "answer_attempts")
class AnswerAttemptEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "resume_version_id")
    val resumeVersionId: Long? = null,
    @Column(name = "source_daily_card_id")
    val sourceDailyCardId: Long? = null,
    @Column(name = "attempt_no", nullable = false)
    val attemptNo: Int,
    @Column(name = "answer_mode", nullable = false)
    val answerMode: String,
    @Column(name = "content_text", nullable = false)
    val contentText: String,
    @Column(name = "submitted_at", nullable = false)
    val submittedAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
