package com.example.interviewplatform.user.dto

import java.time.Instant

data class ProfileImageUploadResponseDto(
    val imageUrl: String,
    val fileName: String,
    val contentType: String,
    val uploadedAt: Instant,
)
