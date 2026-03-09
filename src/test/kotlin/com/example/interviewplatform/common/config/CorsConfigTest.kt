package com.example.interviewplatform.common.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

class CorsConfigTest {
    @Test
    fun `prod profile requires configured cors origins or origin patterns`() {
        val config = CorsConfig(
            environment = MockEnvironment().withProperty("spring.profiles.active", "prod").also {
                it.setActiveProfiles("prod")
            },
            allowedOrigins = "",
            allowedOriginPatterns = "",
            allowedMethods = "GET,POST,OPTIONS",
            allowedHeaders = "*",
            allowCredentials = true,
            maxAgeSeconds = 3600,
        )

        assertThrows(IllegalStateException::class.java) {
            config.validateConfiguration()
        }
    }

    @Test
    fun `prod profile accepts explicit configured origin`() {
        val config = CorsConfig(
            environment = MockEnvironment().withProperty("spring.profiles.active", "prod").also {
                it.setActiveProfiles("prod")
            },
            allowedOrigins = "https://app.example.com",
            allowedOriginPatterns = "",
            allowedMethods = "GET,POST,OPTIONS",
            allowedHeaders = "*",
            allowCredentials = true,
            maxAgeSeconds = 3600,
        )

        assertDoesNotThrow {
            config.validateConfiguration()
        }
    }
}
