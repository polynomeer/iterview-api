package com.example.interviewplatform.resume.dto

import jakarta.validation.constraints.Size

data class CreateResumeVersionRequest(
    @field:Size(max = 2000)
    val fileUrl: String?,
    @field:Size(max = 255)
    val fileName: String? = null,
    @field:Size(max = 100)
    val fileType: String? = null,
    val rawText: String?,
    val parsedJson: String?,
    val summaryText: String?,
)
