package com.example.interviewplatform.resume.dto

data class CreateResumeAnalysisRequest(
    val jobPostingId: Long? = null,
    val preferredFormatType: String? = null,
)
