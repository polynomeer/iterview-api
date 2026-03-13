package com.example.interviewplatform.common.exception

import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.user.repository.UserSettingsRepository
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import kotlin.test.assertEquals
import org.mockito.Mockito.mock

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler(
        ApiErrorResponseFactory(),
        AppLocaleService(
            currentUserProvider = CurrentUserProvider(),
            userSettingsRepository = mock(UserSettingsRepository::class.java),
            messageSource = StaticMessageSource().apply {
                addMessage("error.request_validation_failed", java.util.Locale.KOREAN, "요청 검증에 실패했습니다")
                addMessage("error.resource_not_found", java.util.Locale.KOREAN, "리소스를 찾을 수 없습니다")
                addMessage("error.invalid_request", java.util.Locale.KOREAN, "잘못된 요청입니다")
                addMessage("error.authentication_required", java.util.Locale.KOREAN, "인증이 필요합니다")
                addMessage("error.access_denied", java.util.Locale.KOREAN, "접근이 거부되었습니다")
                addMessage("error.upload_too_large", java.util.Locale.KOREAN, "업로드 파일이 너무 큽니다")
                addMessage("error.unexpected_server_error", java.util.Locale.KOREAN, "예상하지 못한 서버 오류가 발생했습니다")
                addMessage("error.upload_too_large", java.util.Locale.ENGLISH, "Uploaded file is too large")
            },
        ),
    )

    @Test
    fun `max upload size exceeded maps to payload too large response`() {
        val request = MockHttpServletRequest("POST", "/api/resumes/6/versions/upload")

        val response = handler.handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException(1_048_576),
            request,
        )

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.statusCode)
        assertEquals(false, response.body?.success)
        assertEquals("PAYLOAD_TOO_LARGE", response.body?.error?.code)
        assertEquals(413, response.body?.error?.status)
        assertEquals("/api/resumes/6/versions/upload", response.body?.error?.path)
        assertEquals("업로드 파일이 너무 큽니다", response.body?.error?.message)
    }

    @Test
    fun `max upload size exceeded can be localized to english`() {
        val request = MockHttpServletRequest("POST", "/api/resumes/6/versions/upload")
        request.addHeader("X-App-Locale", "en")

        val response = handler.handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException(1_048_576),
            request,
        )

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.statusCode)
        assertEquals("Uploaded file is too large", response.body?.error?.message)
    }
}
