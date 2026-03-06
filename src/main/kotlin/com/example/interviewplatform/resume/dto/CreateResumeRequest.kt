package com.example.interviewplatform.resume.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateResumeRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,
    val isPrimary: Boolean = false,
)
