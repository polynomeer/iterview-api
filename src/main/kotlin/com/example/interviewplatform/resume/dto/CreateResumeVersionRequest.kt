package com.example.interviewplatform.resume.dto

import jakarta.validation.constraints.Size

data class CreateResumeVersionRequest(
    @field:Size(max = 2000)
    val fileUrl: String?,
    val rawText: String?,
    val parsedJson: String?,
    val summaryText: String?,
)
