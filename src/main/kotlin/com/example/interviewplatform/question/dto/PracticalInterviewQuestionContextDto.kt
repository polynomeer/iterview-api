package com.example.interviewplatform.question.dto

import java.time.LocalDate

data class PracticalInterviewQuestionContextDto(
    val sourceInterviewRecordId: Long,
    val sourceInterviewQuestionId: Long,
    val companyName: String?,
    val roleName: String?,
    val interviewDate: LocalDate?,
    val interviewType: String,
    val questionType: String,
    val topicTags: List<String>,
    val intentTags: List<String>,
    val interviewerProfileId: Long?,
    val importedAnswerSummary: String?,
    val importedAnswerText: String?,
    val isFollowUp: Boolean,
)
