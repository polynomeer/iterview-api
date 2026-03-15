package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewRecordRepository : JpaRepository<InterviewRecordEntity, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<InterviewRecordEntity>

    fun findByIdAndUserId(id: Long, userId: Long): InterviewRecordEntity?
}
