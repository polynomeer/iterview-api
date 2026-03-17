package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewRecordFollowUpEdgeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewRecordFollowUpEdgeRepository : JpaRepository<InterviewRecordFollowUpEdgeEntity, Long> {
    fun findByInterviewRecordIdOrderByIdAsc(interviewRecordId: Long): List<InterviewRecordFollowUpEdgeEntity>
    fun findByInterviewRecordIdInOrderByInterviewRecordIdAscIdAsc(interviewRecordIds: List<Long>): List<InterviewRecordFollowUpEdgeEntity>

    fun deleteByInterviewRecordId(interviewRecordId: Long)
}
