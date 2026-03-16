package com.example.interviewplatform.interview.service

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PracticalInterviewTranscriptProcessingListener(
    private val interviewRecordService: InterviewRecordService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRequested(event: PracticalInterviewTranscriptRequestedEvent) {
        interviewRecordService.processQueuedTranscriptExtraction(event.recordId)
    }
}
