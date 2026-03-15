package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewRecordQuestionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewRecordQuestionRepository : JpaRepository<InterviewRecordQuestionEntity, Long> {
    fun findByInterviewRecordIdOrderByOrderIndexAsc(interviewRecordId: Long): List<InterviewRecordQuestionEntity>
}
