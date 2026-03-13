package com.example.interviewplatform.interview.dto

data class InterviewSessionQuestionDto(
    val id: Long,
    val questionId: Long?,
    val title: String,
    val promptText: String?,
    val difficulty: String,
    val orderIndex: Int,
    val status: String,
    val sourceType: String,
    val parentSessionQuestionId: Long?,
    val isFollowUp: Boolean,
    val depth: Int,
    val categoryName: String?,
    val answerAttemptId: Long?,
)
