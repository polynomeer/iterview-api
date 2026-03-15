package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interview_record_follow_up_edges")
class InterviewRecordFollowUpEdgeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "interview_record_id", nullable = false)
    val interviewRecordId: Long,
    @Column(name = "from_question_id", nullable = false)
    val fromQuestionId: Long,
    @Column(name = "to_question_id", nullable = false)
    val toQuestionId: Long,
    @Column(name = "relation_type", nullable = false)
    val relationType: String,
    @Column(name = "trigger_type", nullable = false)
    val triggerType: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
