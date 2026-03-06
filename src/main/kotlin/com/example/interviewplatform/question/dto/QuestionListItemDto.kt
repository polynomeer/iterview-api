package com.example.interviewplatform.question.dto

data class QuestionListItemDto(
    val id: Long,
    val title: String,
    val questionType: String,
    val difficulty: String,
    val qualityStatus: String,
    val categoryId: Long,
    val categoryName: String?,
    val expectedAnswerSeconds: Int?,
    val tags: List<QuestionTagDto>,
    val companies: List<QuestionCompanyDto>,
    val learningMaterials: List<LearningMaterialDto>,
)
