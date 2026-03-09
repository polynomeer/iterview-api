package com.example.interviewplatform.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(description = "Login payload for local email and password authentication")
data class LoginRequest(
    @field:Email
    @field:NotBlank
    @field:Schema(description = "User email address", example = "candidate@example.com")
    val email: String,
    @field:NotBlank
    @field:Schema(description = "Plain text password", example = "password123")
    val password: String,
)
