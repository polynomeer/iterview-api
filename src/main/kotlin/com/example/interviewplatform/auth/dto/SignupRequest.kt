package com.example.interviewplatform.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Signup payload for creating a local account")
data class SignupRequest(
    @field:Email
    @field:NotBlank
    @field:Schema(description = "User email address", example = "candidate@example.com")
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Schema(description = "Plain text password between 8 and 72 characters", example = "password123")
    val password: String,
)
