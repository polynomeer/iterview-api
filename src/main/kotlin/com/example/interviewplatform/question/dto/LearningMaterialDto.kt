package com.example.interviewplatform.question.dto

data class LearningMaterialDto(
    val id: Long,
    val title: String,
    val materialType: String,
    val sourceType: String,
    val sourceLabel: String,
    val description: String?,
    val contentText: String?,
    val contentUrl: String?,
    val sourceName: String?,
    val contentLocale: String?,
    val isUserGenerated: Boolean,
    val difficultyLevel: String?,
    val estimatedMinutes: Int?,
    val isOfficial: Boolean,
    val displayOrder: Int?,
    val relationshipType: String?,
    val labelOverride: String?,
    val relevanceScore: Double?,
)
