package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interviewer_profiles")
class InterviewerProfileEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "source_interview_record_id", nullable = false)
    val sourceInterviewRecordId: Long,
    @Column(name = "style_tags_json", nullable = false)
    val styleTagsJson: String,
    @Column(name = "tone_profile", nullable = false)
    val toneProfile: String,
    @Column(name = "pressure_level", nullable = false)
    val pressureLevel: String,
    @Column(name = "depth_preference", nullable = false)
    val depthPreference: String,
    @Column(name = "follow_up_pattern_json", nullable = false)
    val followUpPatternJson: String,
    @Column(name = "favorite_topics_json", nullable = false)
    val favoriteTopicsJson: String,
    @Column(name = "opening_pattern")
    val openingPattern: String? = null,
    @Column(name = "closing_pattern")
    val closingPattern: String? = null,
    @Column(name = "structuring_source", nullable = false)
    val structuringSource: String = "deterministic",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
