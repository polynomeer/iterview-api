package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewSessionQuestionRepository : JpaRepository<InterviewSessionQuestionEntity, Long> {
    fun findByInterviewSessionIdOrderByOrderIndexAsc(interviewSessionId: Long): List<InterviewSessionQuestionEntity>

    fun findTopByInterviewSessionIdOrderByOrderIndexDesc(interviewSessionId: Long): InterviewSessionQuestionEntity?
}
