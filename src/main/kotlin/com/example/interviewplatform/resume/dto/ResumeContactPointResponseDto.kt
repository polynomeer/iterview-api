package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeContactPointResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeContactPointDto>,
    val generatedAt: Instant,
)
