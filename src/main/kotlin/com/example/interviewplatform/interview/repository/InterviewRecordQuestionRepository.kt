package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewRecordQuestionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewRecordQuestionRepository : JpaRepository<InterviewRecordQuestionEntity, Long> {
    fun findByInterviewRecordIdOrderByOrderIndexAsc(interviewRecordId: Long): List<InterviewRecordQuestionEntity>
    fun findByInterviewRecordIdInOrderByInterviewRecordIdAscOrderIndexAsc(interviewRecordIds: List<Long>): List<InterviewRecordQuestionEntity>
    fun findByLinkedQuestionId(linkedQuestionId: Long): InterviewRecordQuestionEntity?
}
