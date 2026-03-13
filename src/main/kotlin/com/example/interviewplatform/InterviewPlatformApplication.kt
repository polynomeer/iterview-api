package com.example.interviewplatform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InterviewPlatformApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<InterviewPlatformApplication>(*args)
        }
    }
}
