package com.example.interviewplatform.resume.dto

import java.time.Instant

data class ResumeCertificationItemResponseDto(
    val resumeVersionId: Long,
    val items: List<ResumeCertificationItemDto>,
    val generatedAt: Instant,
)
