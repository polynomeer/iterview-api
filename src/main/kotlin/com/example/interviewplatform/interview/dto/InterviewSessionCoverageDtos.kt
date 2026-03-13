package com.example.interviewplatform.interview.dto

data class InterviewSessionCoverageEvidenceItemDto(
    val id: Long,
    val section: String,
    val label: String?,
    val snippet: String,
    val coverageStatus: String,
    val linkedQuestionIds: List<Long>,
)

data class InterviewSessionCoverageResponseDto(
    val sessionId: Long,
    val interviewMode: String,
    val overallCoveragePercent: Int,
    val defendedCoveragePercent: Int,
    val evidenceItems: List<InterviewSessionCoverageEvidenceItemDto>,
)

data class InterviewSessionResumeMapQuestionDto(
    val sessionQuestionId: Long,
    val title: String,
    val sourceType: String,
)

data class InterviewSessionResumeMapEvidenceItemDto(
    val section: String,
    val label: String?,
    val snippet: String,
    val sourceRecordType: String,
    val sourceRecordId: Long,
    val coverageStatus: String,
    val relatedQuestions: List<InterviewSessionResumeMapQuestionDto>,
)

data class InterviewSessionResumeMapResponseDto(
    val sessionId: Long,
    val resumeVersionId: Long?,
    val evidenceItems: List<InterviewSessionResumeMapEvidenceItemDto>,
)
