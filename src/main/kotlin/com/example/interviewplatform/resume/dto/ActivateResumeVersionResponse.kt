package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ActivateResumeVersionResponse(
    val resumeId: Long,
    val versionId: Long,
    val versionNo: Int,
    val activatedAt: Instant,
)
