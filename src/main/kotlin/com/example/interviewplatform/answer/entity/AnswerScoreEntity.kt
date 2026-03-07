package com.example.interviewplatform.answer.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "answer_scores")
class AnswerScoreEntity(
    @Id
    @Column(name = "answer_attempt_id")
    val answerAttemptId: Long,
    @Column(name = "total_score", nullable = false)
    val totalScore: BigDecimal,
    @Column(name = "structure_score", nullable = false)
    val structureScore: BigDecimal,
    @Column(name = "specificity_score", nullable = false)
    val specificityScore: BigDecimal,
    @Column(name = "technical_accuracy_score", nullable = false)
    val technicalAccuracyScore: BigDecimal,
    @Column(name = "role_fit_score", nullable = false)
    val roleFitScore: BigDecimal,
    @Column(name = "company_fit_score", nullable = false)
    val companyFitScore: BigDecimal,
    @Column(name = "communication_score", nullable = false)
    val communicationScore: BigDecimal,
    @Column(name = "evaluation_result", nullable = false)
    val evaluationResult: String,
    @Column(name = "evaluated_at", nullable = false)
    val evaluatedAt: Instant,
)
