package com.example.interviewplatform.common.exception

import com.example.interviewplatform.common.ApiError
import com.example.interviewplatform.common.ApiErrorDetail
import com.example.interviewplatform.common.ApiErrorResponse
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ApiErrorResponseFactory {
    fun build(
        status: Int,
        code: String,
        message: String,
        path: String,
        details: List<ApiErrorDetail> = emptyList(),
    ): ApiErrorResponse = ApiErrorResponse(
        error = ApiError(
            code = code,
            status = status,
            message = message,
            path = path,
            timestamp = Instant.now(),
            details = details,
        ),
    )
}
