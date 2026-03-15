package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewerProfileEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewerProfileRepository : JpaRepository<InterviewerProfileEntity, Long> {
    fun findBySourceInterviewRecordId(sourceInterviewRecordId: Long): InterviewerProfileEntity?

    fun deleteBySourceInterviewRecordId(sourceInterviewRecordId: Long)
}
