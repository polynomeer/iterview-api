package com.example.interviewplatform.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Authentication response containing the bearer token and the authenticated user snapshot")
data class AuthTokenResponse(
    @field:Schema(description = "Signed bearer token for authenticated API requests")
    val accessToken: String,
    @field:Schema(description = "Authentication scheme expected by protected endpoints", example = "Bearer")
    val tokenType: String = "Bearer",
    @field:Schema(description = "Authenticated user summary")
    val user: AuthUserDto,
)
