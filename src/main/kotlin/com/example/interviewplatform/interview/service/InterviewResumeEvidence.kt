package com.example.interviewplatform.interview.service

data class InterviewResumeEvidenceCandidate(
    val section: String,
    val label: String?,
    val snippet: String,
    val sourceRecordType: String,
    val sourceRecordId: Long,
)

data class GeneratedInterviewResumeEvidence(
    val type: String = "resume_sentence",
    val section: String?,
    val label: String?,
    val snippet: String,
    val sourceRecordType: String?,
    val sourceRecordId: Long?,
    val confidence: Double?,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
)
