package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewSessionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewSessionRepository : JpaRepository<InterviewSessionEntity, Long> {
    fun findByUserIdOrderByStartedAtDesc(userId: Long): List<InterviewSessionEntity>

    fun findByIdAndUserId(id: Long, userId: Long): InterviewSessionEntity?
}
