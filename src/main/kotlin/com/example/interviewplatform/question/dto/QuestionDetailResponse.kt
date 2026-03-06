package com.example.interviewplatform.question.dto

data class QuestionDetailResponse(
    val question: QuestionMetadataDto,
    val tags: List<QuestionTagDto>,
    val companies: List<QuestionCompanyDto>,
    val roles: List<QuestionRoleDto>,
    val learningMaterials: List<LearningMaterialDto>,
    val userProgressSummary: UserProgressSummaryDto?,
)
