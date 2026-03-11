package com.example.interviewplatform.question.dto

data class RecommendedFollowUpDto(
    val questionId: Long,
    val title: String,
    val difficulty: String,
    val relationshipType: String,
    val depth: Int,
    val nodeStatus: String,
)
