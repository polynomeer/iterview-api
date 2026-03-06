package com.example.interviewplatform.common

import java.time.Instant

data class ApiError(
    val message: String,
    val timestamp: Instant = Instant.now(),
)
