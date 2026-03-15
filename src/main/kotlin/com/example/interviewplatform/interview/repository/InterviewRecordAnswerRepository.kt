package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewRecordAnswerEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewRecordAnswerRepository : JpaRepository<InterviewRecordAnswerEntity, Long> {
    fun findByInterviewRecordQuestionIdIn(interviewRecordQuestionIds: List<Long>): List<InterviewRecordAnswerEntity>
}
