package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeVersionExtractionDto(
    val resumeVersionId: Long,
    val rawParsingStatus: String,
    val llmExtractionStatus: String?,
    val llmModel: String?,
    val llmPromptVersion: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val errorMessage: String?,
)
