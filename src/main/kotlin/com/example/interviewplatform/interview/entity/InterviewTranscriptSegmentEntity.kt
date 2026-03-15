package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "interview_transcript_segments")
class InterviewTranscriptSegmentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "interview_record_id", nullable = false)
    val interviewRecordId: Long,
    @Column(name = "start_ms", nullable = false)
    val startMs: Long,
    @Column(name = "end_ms", nullable = false)
    val endMs: Long,
    @Column(name = "speaker_type", nullable = false)
    val speakerType: String,
    @Column(name = "raw_text")
    val rawText: String? = null,
    @Column(name = "cleaned_text")
    val cleanedText: String? = null,
    @Column(name = "confirmed_text")
    val confirmedText: String? = null,
    @Column(name = "confidence_score")
    val confidenceScore: BigDecimal? = null,
    @Column(nullable = false)
    val sequence: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
