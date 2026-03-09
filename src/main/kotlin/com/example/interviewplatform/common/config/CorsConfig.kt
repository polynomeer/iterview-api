package com.example.interviewplatform.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
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
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = splitCsv(allowedOrigins)
        config.allowedOriginPatterns = splitCsv(allowedOriginPatterns)
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

    private fun splitCsv(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
