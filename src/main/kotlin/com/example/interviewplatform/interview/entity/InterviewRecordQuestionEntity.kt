package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interview_record_questions")
class InterviewRecordQuestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "interview_record_id", nullable = false)
    val interviewRecordId: Long,
    @Column(name = "segment_start_id")
    val segmentStartId: Long? = null,
    @Column(name = "segment_end_id")
    val segmentEndId: Long? = null,
    @Column(nullable = false)
    val text: String,
    @Column(name = "normalized_text")
    val normalizedText: String? = null,
    @Column(name = "question_type", nullable = false)
    val questionType: String,
    @Column(name = "topic_tags_json", nullable = false)
    val topicTagsJson: String,
    @Column(name = "intent_tags_json", nullable = false)
    val intentTagsJson: String,
    @Column(name = "derived_from_resume_section")
    val derivedFromResumeSection: String? = null,
    @Column(name = "derived_from_resume_record_type")
    val derivedFromResumeRecordType: String? = null,
    @Column(name = "derived_from_resume_record_id")
    val derivedFromResumeRecordId: Long? = null,
    @Column(name = "derived_from_job_posting_section")
    val derivedFromJobPostingSection: String? = null,
    @Column(name = "linked_question_id")
    val linkedQuestionId: Long? = null,
    @Column(name = "parent_question_id")
    val parentQuestionId: Long? = null,
    @Column(name = "structuring_source", nullable = false)
    val structuringSource: String = "deterministic",
    @Column(name = "order_index", nullable = false)
    val orderIndex: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
