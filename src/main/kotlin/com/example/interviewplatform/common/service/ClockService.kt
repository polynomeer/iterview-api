package com.example.interviewplatform.common.service

import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ClockService {
    fun now(): Instant = Instant.now()
}
