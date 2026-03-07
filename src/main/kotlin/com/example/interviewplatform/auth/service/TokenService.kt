package com.example.interviewplatform.auth.service

import com.example.interviewplatform.auth.security.AuthenticatedUser

interface TokenService {
    fun issueToken(userId: Long, email: String): String

    fun parseUser(token: String): AuthenticatedUser?
}
