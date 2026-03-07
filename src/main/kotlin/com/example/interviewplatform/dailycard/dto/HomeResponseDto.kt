package com.example.interviewplatform.dailycard.dto

import com.example.interviewplatform.question.dto.LearningMaterialDto

data class HomeResponseDto(
    val todayQuestion: HomeQuestionDto?,
    val retryQuestions: List<HomeRetryQuestionDto>,
    val learningMaterials: List<LearningMaterialDto>,
    val summaryStats: HomeSummaryStatsDto,
)
