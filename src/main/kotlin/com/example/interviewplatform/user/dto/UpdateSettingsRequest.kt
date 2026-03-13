package com.example.interviewplatform.user.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class UpdateSettingsRequest(
    @field:Min(0)
    @field:Max(100)
    val targetScoreThreshold: Int?,
    @field:Min(0)
    @field:Max(100)
    val passScoreThreshold: Int?,
    val retryEnabled: Boolean?,
    @field:Min(1)
    @field:Max(10)
    val dailyQuestionCount: Int?,
    val preferredLanguage: String?,
)
