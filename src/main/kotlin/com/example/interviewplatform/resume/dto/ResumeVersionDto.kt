package com.example.interviewplatform.resume.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class ResumeVersionDto(
    val id: Long,
    val versionNo: Int,
    val fileUrl: String?,
    val fileName: String?,
    val fileType: String?,
    val fileSizeBytes: Long?,
    val summaryText: String?,
    val parsingStatus: String,
    val parseStartedAt: Instant?,
    val parseCompletedAt: Instant?,
    val parseErrorMessage: String?,
    @field:Schema(description = "Structured extraction lifecycle status", nullable = true, example = "completed")
    val llmExtractionStatus: String?,
    @field:Schema(description = "Structured extraction start time", nullable = true)
    val llmExtractionStartedAt: Instant?,
    @field:Schema(description = "Structured extraction completion time", nullable = true)
    val llmExtractionCompletedAt: Instant?,
    @field:Schema(description = "Structured extraction error message", nullable = true)
    val llmExtractionErrorMessage: String?,
    @field:Schema(description = "LLM model identifier used for structured extraction", nullable = true)
    val llmModel: String?,
    @field:Schema(description = "Prompt version used for structured extraction", nullable = true)
    val llmPromptVersion: String?,
    @field:Schema(description = "Aggregate confidence for the structured extraction", nullable = true)
    val llmExtractionConfidence: Double?,
    val isActive: Boolean,
    val uploadedAt: Instant,
)
