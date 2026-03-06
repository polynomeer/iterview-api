package com.example.interviewplatform.user.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_target_companies")
class UserTargetCompanyEntity(
    @EmbeddedId
    val id: UserTargetCompanyId,
    @Column(name = "priority_order", nullable = false)
    val priorityOrder: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
