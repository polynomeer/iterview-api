package com.example.interviewplatform.answer.service

data class AnswerProgressSource(
    val sourceType: String,
    val sourceLabel: String,
    val sourceSessionId: Long? = null,
    val sourceSessionQuestionId: Long? = null,
    val isFollowUp: Boolean = false,
)
