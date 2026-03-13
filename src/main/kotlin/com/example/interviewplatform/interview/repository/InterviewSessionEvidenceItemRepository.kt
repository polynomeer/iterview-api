package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewSessionEvidenceItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewSessionEvidenceItemRepository : JpaRepository<InterviewSessionEvidenceItemEntity, Long> {
    fun findByInterviewSessionIdOrderByDisplayOrderAscIdAsc(interviewSessionId: Long): List<InterviewSessionEvidenceItemEntity>

    fun findFirstByInterviewSessionIdAndCoverageStatusOrderByCoveragePriorityDescDisplayOrderAscIdAsc(
        interviewSessionId: Long,
        coverageStatus: String,
    ): InterviewSessionEvidenceItemEntity?
}
