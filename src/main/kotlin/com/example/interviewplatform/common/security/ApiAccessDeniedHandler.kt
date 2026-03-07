package com.example.interviewplatform.common.security

import com.example.interviewplatform.common.exception.ApiErrorResponseFactory
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class ApiAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
    private val errorFactory: ApiErrorResponseFactory,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = "application/json"
        val body = errorFactory.build(
            status = HttpStatus.FORBIDDEN.value(),
            code = "FORBIDDEN",
            message = "Access denied",
            path = request.requestURI,
        )
        objectMapper.writeValue(response.outputStream, body)
    }
}
