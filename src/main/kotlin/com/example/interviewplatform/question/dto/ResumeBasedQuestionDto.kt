package com.example.interviewplatform.question.dto

data class ResumeBasedQuestionDto(
    val questionId: Long,
    val title: String,
    val difficulty: String,
    val matchScore: Double,
    val matchedSkills: List<String>,
)
