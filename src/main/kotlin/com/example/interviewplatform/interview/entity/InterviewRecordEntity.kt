package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "interview_records")
class InterviewRecordEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "company_name")
    val companyName: String? = null,
    @Column(name = "role_name")
    val roleName: String? = null,
    @Column(name = "interview_date")
    val interviewDate: LocalDate? = null,
    @Column(name = "interview_type", nullable = false)
    val interviewType: String,
    @Column(name = "source_audio_file_url")
    val sourceAudioFileUrl: String? = null,
    @Column(name = "source_audio_file_name")
    val sourceAudioFileName: String? = null,
    @Column(name = "source_audio_duration_ms")
    val sourceAudioDurationMs: Long? = null,
    @Column(name = "source_audio_content_type")
    val sourceAudioContentType: String? = null,
    @Column(name = "raw_transcript")
    val rawTranscript: String? = null,
    @Column(name = "cleaned_transcript")
    val cleanedTranscript: String? = null,
    @Column(name = "confirmed_transcript")
    val confirmedTranscript: String? = null,
    @Column(name = "transcript_status", nullable = false)
    val transcriptStatus: String,
    @Column(name = "analysis_status", nullable = false)
    val analysisStatus: String,
    @Column(name = "linked_resume_version_id")
    val linkedResumeVersionId: Long? = null,
    @Column(name = "linked_job_posting_id")
    val linkedJobPostingId: Long? = null,
    @Column(name = "interviewer_profile_id")
    val interviewerProfileId: Long? = null,
    @Column(name = "deterministic_summary")
    val deterministicSummary: String? = null,
    @Column(name = "ai_enriched_summary")
    val aiEnrichedSummary: String? = null,
    @Column(name = "overall_summary")
    val overallSummary: String? = null,
    @Column(name = "structuring_stage", nullable = false)
    val structuringStage: String = "deterministic",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
