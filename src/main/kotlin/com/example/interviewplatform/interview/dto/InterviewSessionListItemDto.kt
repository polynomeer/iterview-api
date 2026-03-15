package com.example.interviewplatform.interview.dto

import java.time.Instant

data class InterviewSessionListItemDto(
    val id: Long,
    val sessionType: String,
    val interviewMode: String,
    val sourceInterviewRecordId: Long?,
    val replayMode: String?,
    val status: String,
    val resumeVersionId: Long?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val questionCount: Int,
    val answeredCount: Int,
    val averageScore: Double?,
)
