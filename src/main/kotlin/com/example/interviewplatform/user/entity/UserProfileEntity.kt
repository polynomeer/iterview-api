package com.example.interviewplatform.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "user_profiles")
class UserProfileEntity(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    @Column
    val nickname: String? = null,
    @Column(name = "job_role_id")
    val jobRoleId: Long? = null,
    @Column(name = "years_of_experience")
    val yearsOfExperience: Int? = null,
    @Column(name = "avg_score")
    val avgScore: BigDecimal? = null,
    @Column(name = "archived_question_count", nullable = false)
    val archivedQuestionCount: Int = 0,
    @Column(name = "answer_visibility_default", nullable = false)
    val answerVisibilityDefault: String = "private",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
