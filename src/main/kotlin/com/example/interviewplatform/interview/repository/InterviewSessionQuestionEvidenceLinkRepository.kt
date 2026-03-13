package com.example.interviewplatform.interview.repository

import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEvidenceLinkEntity
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEvidenceLinkId
import org.springframework.data.jpa.repository.JpaRepository

interface InterviewSessionQuestionEvidenceLinkRepository :
    JpaRepository<InterviewSessionQuestionEvidenceLinkEntity, InterviewSessionQuestionEvidenceLinkId> {
    fun findByIdInterviewSessionQuestionIdIn(interviewSessionQuestionIds: List<Long>): List<InterviewSessionQuestionEvidenceLinkEntity>

    fun findByIdInterviewSessionEvidenceItemIdIn(interviewSessionEvidenceItemIds: List<Long>): List<InterviewSessionQuestionEvidenceLinkEntity>
}
