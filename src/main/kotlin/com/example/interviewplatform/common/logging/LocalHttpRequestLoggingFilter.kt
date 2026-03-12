package com.example.interviewplatform.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant

@Component
@Profile("local")
class LocalHttpRequestLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = Instant.now()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis()
            log.info(
                "http_request method={} path={} query={} status={} durationMs={} origin={} remoteAddr={}",
                request.method,
                request.requestURI,
                request.queryString ?: "",
                response.status,
                durationMs,
                request.getHeader("Origin") ?: "",
                request.remoteAddr ?: "",
            )
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LocalHttpRequestLoggingFilter::class.java)
    }
}
