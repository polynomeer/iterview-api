package com.example.interviewplatform.resume.dto

data class ResumeTailoredDocumentSectionDto(
    val sectionKey: String,
    val title: String,
    val lines: List<String>,
)
