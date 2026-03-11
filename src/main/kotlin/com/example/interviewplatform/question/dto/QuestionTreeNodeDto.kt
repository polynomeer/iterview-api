package com.example.interviewplatform.question.dto

data class QuestionTreeNodeDto(
    val questionId: Long,
    val title: String,
    val difficulty: String,
    val nodeStatus: String,
    val depth: Int,
    val children: List<QuestionTreeNodeDto>,
)
