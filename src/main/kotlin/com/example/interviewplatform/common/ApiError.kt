package com.example.interviewplatform.common

import java.time.Instant

data class ApiErrorResponse(
    val success: Boolean = false,
    val error: ApiError,
)

data class ApiError(
    val code: String,
    val status: Int,
    val message: String,
    val path: String,
    val timestamp: Instant,
    val details: List<ApiErrorDetail> = emptyList(),
)

data class ApiErrorDetail(
    val field: String,
    val message: String,
)
