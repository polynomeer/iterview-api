package com.example.interviewplatform.answer.service

data class AnswerPolicyDecision(
    val archive: Boolean,
    val needsRetry: Boolean,
    val retryReasonType: String?,
    val retryPriority: Int?,
    val retryDelayDays: Long?,
    val weakDimensions: List<String>,
)
