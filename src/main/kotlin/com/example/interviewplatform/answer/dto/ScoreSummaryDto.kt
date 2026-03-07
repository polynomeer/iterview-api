package com.example.interviewplatform.answer.dto

data class ScoreSummaryDto(
    val totalScore: Int,
    val structureScore: Int,
    val specificityScore: Int,
    val technicalAccuracyScore: Int,
    val roleFitScore: Int,
    val companyFitScore: Int,
    val communicationScore: Int,
    val evaluationResult: String,
)
