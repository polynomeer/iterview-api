package com.example.interviewplatform.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_settings")
class UserSettingsEntity(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    @Column(name = "target_score_threshold", nullable = false)
    val targetScoreThreshold: Int = 80,
    @Column(name = "pass_score_threshold", nullable = false)
    val passScoreThreshold: Int = 60,
    @Column(name = "retry_enabled", nullable = false)
    val retryEnabled: Boolean = true,
    @Column(name = "daily_question_count", nullable = false)
    val dailyQuestionCount: Int = 1,
    @Column(name = "preferred_language", nullable = false)
    val preferredLanguage: String = "ko",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
