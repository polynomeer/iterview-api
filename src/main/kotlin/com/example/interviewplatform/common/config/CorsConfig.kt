package com.example.interviewplatform.common.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    private val environment: Environment,
    @Value("\${app.cors.allowed-origins:}")
    private val allowedOrigins: String,
    @Value("\${app.cors.allowed-origin-patterns:http://localhost:[*],http://127.0.0.1:[*],https://localhost:[*],https://127.0.0.1:[*]}")
    private val allowedOriginPatterns: String,
    @Value("\${app.cors.allowed-methods:GET,POST,PATCH,PUT,DELETE,OPTIONS}")
    private val allowedMethods: String,
    @Value("\${app.cors.allowed-headers:*}")
    private val allowedHeaders: String,
    @Value("\${app.cors.allow-credentials:true}")
    private val allowCredentials: Boolean,
    @Value("\${app.cors.max-age-seconds:3600}")
    private val maxAgeSeconds: Long,
) {
    @PostConstruct
    fun validateConfiguration() {
        val configuredOrigins = configuredAllowedOrigins()
        val configuredOriginPatterns = configuredAllowedOriginPatterns()

        if (environment.activeProfiles.contains("prod") &&
            configuredOrigins.isEmpty() &&
            configuredOriginPatterns.isEmpty()
        ) {
            throw IllegalStateException(
                "Production CORS configuration requires APP_CORS_ALLOWED_ORIGINS or APP_CORS_ALLOWED_ORIGIN_PATTERNS.",
            )
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = configuredAllowedOrigins()
        config.allowedOriginPatterns = configuredAllowedOriginPatterns()
        config.allowedMethods = splitCsv(allowedMethods)
        config.allowedHeaders = splitCsv(allowedHeaders)
        config.allowCredentials = allowCredentials
        config.maxAge = maxAgeSeconds

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", config)
        source.registerCorsConfiguration("/v3/api-docs/**", config)
        source.registerCorsConfiguration("/swagger-ui/**", config)
        source.registerCorsConfiguration("/swagger-ui.html", config)
        return source
    }

    private fun configuredAllowedOrigins(): List<String> {
        val configured = splitCsv(allowedOrigins)
        if (configured.isNotEmpty() || environment.activeProfiles.contains("prod")) {
            return configured
        }
        return LOCAL_DEFAULT_ORIGINS
    }

    private fun configuredAllowedOriginPatterns(): List<String> {
        val configured = splitCsv(allowedOriginPatterns)
        if (configured.isNotEmpty() || environment.activeProfiles.contains("prod")) {
            return configured
        }
        return LOCAL_DEFAULT_ORIGIN_PATTERNS
    }

    private fun splitCsv(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        val LOCAL_DEFAULT_ORIGINS = listOf(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://localhost:5173",
            "https://127.0.0.1:5173",
        )
        val LOCAL_DEFAULT_ORIGIN_PATTERNS = listOf(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "https://localhost:[*]",
            "https://127.0.0.1:[*]",
        )
    }
}
