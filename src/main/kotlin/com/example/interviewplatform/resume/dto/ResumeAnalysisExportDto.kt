package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeAnalysisExportDto(
    val id: Long,
    val resumeAnalysisId: Long,
    val exportType: String,
    val formatType: String?,
    val fileName: String,
    val fileUrl: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val pageCount: Int?,
    val createdAt: Instant,
)
