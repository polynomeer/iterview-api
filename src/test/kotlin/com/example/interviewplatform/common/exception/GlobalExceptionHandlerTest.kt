package com.example.interviewplatform.common.exception

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler(ApiErrorResponseFactory())

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
        assertEquals("Uploaded file is too large", response.body?.error?.message)
    }
}
