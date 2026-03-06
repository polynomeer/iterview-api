package com.example.interviewplatform.dailycard.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "daily_cards")
class DailyCardEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "question_id", nullable = false)
    val questionId: Long,
    @Column(name = "card_date", nullable = false)
    val cardDate: LocalDate,
    @Column(name = "card_type", nullable = false)
    val cardType: String,
    @Column(name = "source_reason", nullable = false)
    val sourceReason: String,
    @Column(nullable = false)
    val status: String,
    @Column(name = "delivered_at")
    val deliveredAt: Instant? = null,
    @Column(name = "opened_at")
    val openedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
