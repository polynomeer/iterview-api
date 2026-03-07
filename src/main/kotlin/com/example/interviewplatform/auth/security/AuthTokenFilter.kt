package com.example.interviewplatform.auth.security

import com.example.interviewplatform.auth.service.TokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthTokenFilter(
    private val tokenService: TokenService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HEADER_AUTHORIZATION)
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            val token = header.removePrefix(TOKEN_PREFIX).trim()
            if (token.isNotBlank() && SecurityContextHolder.getContext().authentication == null) {
                val user = tokenService.parseUser(token)
                if (user != null) {
                    val authentication = UsernamePasswordAuthenticationToken(user, null, emptyList())
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authentication
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val TOKEN_PREFIX = "Bearer "
    }
}
