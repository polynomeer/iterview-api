package com.example.interviewplatform.auth.dto

data class AuthTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val user: AuthUserDto,
)
