package com.example.interviewplatform.resume.dto

import jakarta.validation.constraints.Pattern

data class CreateResumeAnalysisExportRequest(
    @field:Pattern(
        regexp = "^(pdf)$",
        message = "exportType must be one of: pdf",
    )
    val exportType: String = "pdf",
)
