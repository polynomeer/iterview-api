package com.example.interviewplatform.user.dto

data class SettingsDto(
    val targetScoreThreshold: Int,
    val passScoreThreshold: Int,
    val retryEnabled: Boolean,
    val dailyQuestionCount: Int,
)
