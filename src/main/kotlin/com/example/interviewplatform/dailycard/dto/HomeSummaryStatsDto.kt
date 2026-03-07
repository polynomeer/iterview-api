package com.example.interviewplatform.dailycard.dto

data class HomeSummaryStatsDto(
    val dailyQuestionCount: Int,
    val retryQuestionCount: Int,
    val pendingReviewCount: Int,
    val archivedQuestionCount: Int,
)
