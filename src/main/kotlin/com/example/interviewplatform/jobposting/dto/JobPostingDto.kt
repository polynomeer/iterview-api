package com.example.interviewplatform.jobposting.dto

import java.time.Instant

data class JobPostingDto(
    val id: Long,
    val inputType: String,
    val sourceUrl: String?,
    val rawText: String?,
    val fetchStatus: String,
    val fetchedTitle: String?,
    val fetchErrorMessage: String?,
    val fetchedAt: Instant?,
    val companyName: String?,
    val roleName: String?,
    val parsedRequirements: List<String>,
    val parsedNiceToHave: List<String>,
    val parsedKeywords: List<String>,
    val parsedResponsibilities: List<String>,
    val parsedSummary: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
