package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeAnalysisSuggestionDto(
    val id: Long,
    val sectionKey: String,
    val originalText: String?,
    val suggestedText: String,
    val reason: String,
    val suggestionType: String,
    val accepted: Boolean,
    val displayOrder: Int,
    val createdAt: Instant,
)
