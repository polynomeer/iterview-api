package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "interview_session_question_evidence_links")
class InterviewSessionQuestionEvidenceLinkEntity(
    @EmbeddedId
    val id: InterviewSessionQuestionEvidenceLinkId,
    @Column(name = "link_role", nullable = false)
    val linkRole: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

data class InterviewSessionQuestionEvidenceLinkId(
    @Column(name = "interview_session_question_id")
    val interviewSessionQuestionId: Long = 0,
    @Column(name = "interview_session_evidence_item_id")
    val interviewSessionEvidenceItemId: Long = 0,
) : Serializable
