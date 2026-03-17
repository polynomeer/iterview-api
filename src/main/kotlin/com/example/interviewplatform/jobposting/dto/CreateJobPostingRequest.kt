package com.example.interviewplatform.jobposting.dto

import jakarta.validation.constraints.Size

data class CreateJobPostingRequest(
    val inputType: String,
    @field:Size(max = 2000)
    val sourceUrl: String? = null,
    val rawText: String? = null,
    @field:Size(max = 255)
    val companyName: String? = null,
    @field:Size(max = 255)
    val roleName: String? = null,
)
