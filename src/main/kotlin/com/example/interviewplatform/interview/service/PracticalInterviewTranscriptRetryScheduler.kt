package com.example.interviewplatform.interview.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PracticalInterviewTranscriptRetryScheduler(
    private val interviewRecordService: InterviewRecordService,
) {
    @Scheduled(fixedDelayString = "\${app.interview.transcription.retry-scheduler-delay-ms:60000}")
    fun processRetries() {
        interviewRecordService.recoverTimedOutTranscriptExtractions()
        interviewRecordService.enqueueEligibleTranscriptRetries()
    }
}
