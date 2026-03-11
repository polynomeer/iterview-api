package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interview_session_questions")
class InterviewSessionQuestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "interview_session_id", nullable = false)
    val interviewSessionId: Long,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "order_index", nullable = false)
    val orderIndex: Int,
    @Column(name = "answer_attempt_id")
    val answerAttemptId: Long? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
