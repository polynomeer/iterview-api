package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface InterviewRecordRepository : JpaRepository<InterviewRecordEntity, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<InterviewRecordEntity>
    fun findByUserIdAndLinkedResumeVersionIdOrderByCreatedAtDesc(userId: Long, linkedResumeVersionId: Long): List<InterviewRecordEntity>

    fun findByIdAndUserId(id: Long, userId: Long): InterviewRecordEntity?

    @Query(
        """
        select r from InterviewRecordEntity r
        where r.transcriptStatus in ('pending', 'failed')
          and r.transcriptNextRetryAt is not null
          and r.transcriptNextRetryAt <= :now
        order by r.transcriptNextRetryAt asc, r.id asc
        """,
    )
    fun findRetryableTranscriptRecords(now: Instant): List<InterviewRecordEntity>

    @Query(
        """
        select r from InterviewRecordEntity r
        where r.transcriptStatus = 'processing'
          and r.transcriptProcessingStartedAt is not null
          and r.transcriptProcessingStartedAt <= :threshold
        order by r.transcriptProcessingStartedAt asc, r.id asc
        """,
    )
    fun findTimedOutProcessingTranscriptRecords(threshold: Instant): List<InterviewRecordEntity>
}
