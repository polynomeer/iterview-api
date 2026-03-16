package com.example.interviewplatform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableAsync
@EnableScheduling
class InterviewPlatformApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<InterviewPlatformApplication>(*args)
        }
    }
}
