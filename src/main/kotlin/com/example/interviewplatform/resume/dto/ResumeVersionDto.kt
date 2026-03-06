package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeVersionDto(
    val id: Long,
    val versionNo: Int,
    val fileUrl: String?,
    val summaryText: String?,
    val isActive: Boolean,
    val uploadedAt: Instant,
)
