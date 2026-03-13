package com.example.interviewplatform.resume.dto

data class ResumeProjectTagDto(
    val id: Long,
    val tagName: String,
    val tagType: String?,
    val displayOrder: Int,
    val sourceText: String?,
)
