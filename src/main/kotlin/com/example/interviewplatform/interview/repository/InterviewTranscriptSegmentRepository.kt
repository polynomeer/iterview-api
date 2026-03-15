package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewTranscriptSegmentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewTranscriptSegmentRepository : JpaRepository<InterviewTranscriptSegmentEntity, Long> {
    fun findByInterviewRecordIdOrderBySequenceAsc(interviewRecordId: Long): List<InterviewTranscriptSegmentEntity>

    fun findByIdAndInterviewRecordId(id: Long, interviewRecordId: Long): InterviewTranscriptSegmentEntity?
}
