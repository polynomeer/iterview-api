package com.example.interviewplatform.interview.dto

data class InterviewResumeEvidenceDto(
    val type: String,
    val section: String?,
    val label: String?,
    val snippet: String,
    val sourceRecordType: String?,
    val sourceRecordId: Long?,
    val confidence: Double?,
    val startOffset: Int?,
    val endOffset: Int?,
)
