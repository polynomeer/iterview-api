package com.example.interviewplatform.question.dto

data class LearningMaterialDto(
    val id: Long,
    val title: String,
    val materialType: String,
    val contentUrl: String?,
    val sourceName: String?,
)
