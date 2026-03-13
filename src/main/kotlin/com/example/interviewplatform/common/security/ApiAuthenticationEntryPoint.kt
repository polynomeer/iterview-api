package com.example.interviewplatform.common.security

import com.example.interviewplatform.common.exception.ApiErrorResponseFactory
import com.example.interviewplatform.common.service.AppLocaleService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class ApiAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
    private val errorFactory: ApiErrorResponseFactory,
    private val appLocaleService: AppLocaleService,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = "application/json"
        val body = errorFactory.build(
            status = HttpStatus.UNAUTHORIZED.value(),
            code = "UNAUTHORIZED",
            message = appLocaleService.getMessage("error.authentication_required", request),
            path = request.requestURI,
        )
        objectMapper.writeValue(response.outputStream, body)
    }
}
