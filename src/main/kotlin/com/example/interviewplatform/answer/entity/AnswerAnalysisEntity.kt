package com.example.interviewplatform.answer.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "answer_analyses")
class AnswerAnalysisEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "answer_attempt_id", nullable = false)
    val answerAttemptId: Long,
    @Column(name = "overall_score", nullable = false)
    val overallScore: BigDecimal,
    @Column(name = "depth_score", nullable = false)
    val depthScore: BigDecimal,
    @Column(name = "clarity_score", nullable = false)
    val clarityScore: BigDecimal,
    @Column(name = "accuracy_score", nullable = false)
    val accuracyScore: BigDecimal,
    @Column(name = "example_score", nullable = false)
    val exampleScore: BigDecimal,
    @Column(name = "tradeoff_score", nullable = false)
    val tradeoffScore: BigDecimal,
    @Column(name = "confidence_score")
    val confidenceScore: BigDecimal? = null,
    @Column(name = "strength_summary", nullable = false)
    val strengthSummary: String,
    @Column(name = "weakness_summary", nullable = false)
    val weaknessSummary: String,
    @Column(name = "recommended_next_step")
    val recommendedNextStep: String? = null,
    @Column(name = "detailed_feedback")
    val detailedFeedback: String? = null,
    @Column(name = "model_answer_text")
    val modelAnswerText: String? = null,
    @Column(name = "strength_points_json")
    val strengthPointsJson: String? = null,
    @Column(name = "improvement_points_json")
    val improvementPointsJson: String? = null,
    @Column(name = "missed_points_json")
    val missedPointsJson: String? = null,
    @Column(name = "llm_model")
    val llmModel: String? = null,
    @Column(name = "content_locale")
    val contentLocale: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
