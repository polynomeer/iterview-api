package com.example.interviewplatform.resume.dto

data class ResumeTailoredDocumentDto(
    val title: String,
    val targetCompany: String?,
    val targetRole: String?,
    val formatType: String,
    val sectionOrder: List<String>,
    val summary: String?,
    val diffSummary: String?,
    val analysisNotes: List<String>,
    val sections: List<ResumeTailoredDocumentSectionDto>,
    val plainText: String?,
)
