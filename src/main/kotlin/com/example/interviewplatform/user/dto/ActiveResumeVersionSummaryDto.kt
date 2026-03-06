package com.example.interviewplatform.user.dto

import java.time.Instant

data class ActiveResumeVersionSummaryDto(
    val resumeId: Long,
    val resumeTitle: String,
    val versionId: Long,
    val versionNo: Int,
    val uploadedAt: Instant,
)
