package com.example.interviewplatform.question.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

data class CreateQuestionLearningMaterialRequest(
    @field:NotBlank
    val title: String,
    @field:NotBlank
    val materialType: String,
    val description: String? = null,
    val contentText: String? = null,
    val contentUrl: String? = null,
    val sourceName: String? = null,
    val difficultyLevel: String? = null,
    @field:Min(1)
    val estimatedMinutes: Int? = null,
    val relationshipType: String? = null,
    val labelOverride: String? = null,
    val relevanceScore: BigDecimal? = null,
)
