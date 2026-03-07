package com.example.interviewplatform.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:Email
    @field:NotBlank
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,
)
