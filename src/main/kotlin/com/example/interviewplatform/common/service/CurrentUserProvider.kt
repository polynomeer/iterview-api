package com.example.interviewplatform.common.service

import com.example.interviewplatform.auth.security.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class CurrentUserProvider {
    fun currentUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is AuthenticatedUser) {
            return principal.id
        }
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")
    }

    fun currentUserIdOrNull(): Long? {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return (principal as? AuthenticatedUser)?.id
    }
}
