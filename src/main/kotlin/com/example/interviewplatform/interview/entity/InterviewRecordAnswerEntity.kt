package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interview_record_answers")
class InterviewRecordAnswerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "interview_record_question_id", nullable = false)
    val interviewRecordQuestionId: Long,
    @Column(name = "segment_start_id")
    val segmentStartId: Long? = null,
    @Column(name = "segment_end_id")
    val segmentEndId: Long? = null,
    @Column(nullable = false)
    val text: String,
    @Column(name = "normalized_text")
    val normalizedText: String? = null,
    @Column
    val summary: String? = null,
    @Column(name = "confidence_markers_json", nullable = false)
    val confidenceMarkersJson: String,
    @Column(name = "weakness_tags_json", nullable = false)
    val weaknessTagsJson: String,
    @Column(name = "strength_tags_json", nullable = false)
    val strengthTagsJson: String,
    @Column(name = "analysis_json")
    val analysisJson: String? = null,
    @Column(name = "order_index", nullable = false)
    val orderIndex: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
