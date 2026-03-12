package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeVersionDto(
    val id: Long,
    val versionNo: Int,
    val fileUrl: String?,
    val fileName: String?,
    val fileType: String?,
    val fileSizeBytes: Long?,
    val summaryText: String?,
    val parsingStatus: String,
    val parseStartedAt: Instant?,
    val parseCompletedAt: Instant?,
    val parseErrorMessage: String?,
    val isActive: Boolean,
    val uploadedAt: Instant,
)
