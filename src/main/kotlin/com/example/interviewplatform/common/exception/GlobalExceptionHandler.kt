package com.example.interviewplatform.common.exception

import com.example.interviewplatform.common.ApiErrorDetail
import com.example.interviewplatform.common.ApiErrorResponse
import com.example.interviewplatform.common.service.AppLocaleService
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory

@RestControllerAdvice
class GlobalExceptionHandler(
    private val errorFactory: ApiErrorResponseFactory,
    private val appLocaleService: AppLocaleService,
) {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        log.warn("validation_error path={} message={}", request.requestURI, ex.message, ex)
        val details = ex.bindingResult.allErrors.mapNotNull { error ->
            when (error) {
                is FieldError -> ApiErrorDetail(field = error.field, message = error.defaultMessage ?: "Invalid value")
                else -> null
            }
        }
        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = appLocaleService.getMessage("error.request_validation_failed", request),
            path = request.requestURI,
            details = details,
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        log.warn("constraint_violation path={} message={}", request.requestURI, ex.message, ex)
        val details = ex.constraintViolations.map {
            ApiErrorDetail(field = it.propertyPath.toString(), message = it.message)
        }
        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = appLocaleService.getMessage("error.request_validation_failed", request),
            path = request.requestURI,
            details = details,
        )
    }

    @ExceptionHandler(EntityNotFoundException::class, NoSuchElementException::class)
    fun handleEntityNotFound(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("not_found path={} message={}", request.requestURI, ex.message, ex)
        return respond(
            status = HttpStatus.NOT_FOUND,
            code = "NOT_FOUND",
            message = ex.message ?: appLocaleService.getMessage("error.resource_not_found", request),
            path = request.requestURI,
        )
    }

    @ExceptionHandler(DomainValidationException::class, IllegalArgumentException::class)
    fun handleDomainValidation(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("bad_request path={} message={}", request.requestURI, ex.message, ex)
        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "BAD_REQUEST",
            message = ex.message ?: appLocaleService.getMessage("error.invalid_request", request),
            path = request.requestURI,
        )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("unauthorized path={} message={}", request.requestURI, ex.message, ex)
        return respond(
            status = HttpStatus.UNAUTHORIZED,
            code = "UNAUTHORIZED",
            message = ex.message ?: appLocaleService.getMessage("error.authentication_required", request),
            path = request.requestURI,
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn("forbidden path={} message={}", request.requestURI, ex.message, ex)
        return respond(
            status = HttpStatus.FORBIDDEN,
            code = "FORBIDDEN",
            message = ex.message ?: appLocaleService.getMessage("error.access_denied", request),
            path = request.requestURI,
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.warn(
            "response_status_exception path={} status={} message={}",
            request.requestURI,
            ex.statusCode.value(),
            ex.reason ?: ex.message,
            ex,
        )
        val status = HttpStatus.valueOf(ex.statusCode.value())
        return respond(
            status = status,
            code = status.name,
            message = ex.reason ?: defaultMessage(status, request),
            path = request.requestURI,
        )
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(
        ex: MaxUploadSizeExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        log.warn("payload_too_large path={} message={}", request.requestURI, ex.message, ex)
        return respond(
            status = HttpStatus.PAYLOAD_TOO_LARGE,
            code = "PAYLOAD_TOO_LARGE",
            message = appLocaleService.getMessage("error.upload_too_large", request),
            path = request.requestURI,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        log.error("unexpected_exception path={} message={}", request.requestURI, ex.message, ex)
        return respond(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = appLocaleService.getMessage("error.unexpected_server_error", request),
            path = request.requestURI,
        )
    }

    private fun respond(
        status: HttpStatus,
        code: String,
        message: String,
        path: String,
        details: List<ApiErrorDetail> = emptyList(),
    ): ResponseEntity<ApiErrorResponse> = ResponseEntity.status(status).body(
        errorFactory.build(
            status = status.value(),
            code = code,
            message = message,
            path = path,
            details = details,
        ),
    )

    private fun defaultMessage(status: HttpStatus, request: HttpServletRequest): String = when (status) {
        HttpStatus.NOT_FOUND -> appLocaleService.getMessage("error.resource_not_found", request)
        HttpStatus.BAD_REQUEST -> appLocaleService.getMessage("error.invalid_request", request)
        HttpStatus.UNAUTHORIZED -> appLocaleService.getMessage("error.authentication_required", request)
        HttpStatus.FORBIDDEN -> appLocaleService.getMessage("error.access_denied", request)
        else -> status.reasonPhrase
    }

    private companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
