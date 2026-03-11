package com.example.interviewplatform.interview.dto

data class InterviewSessionQuestionDto(
    val id: Long,
    val questionId: Long,
    val title: String,
    val difficulty: String,
    val orderIndex: Int,
    val status: String,
    val answerAttemptId: Long?,
)
