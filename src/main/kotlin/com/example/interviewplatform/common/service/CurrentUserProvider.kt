package com.example.interviewplatform.common.service

import org.springframework.stereotype.Component

@Component
class CurrentUserProvider {
    // Temporary development strategy until auth is implemented.
    fun currentUserId(): Long = 1L
}
