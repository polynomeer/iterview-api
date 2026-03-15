package com.example.interviewplatform.question.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Detailed question payload with metadata, related resources, and optional user progress")
data class QuestionDetailResponse(
    @field:Schema(description = "Core question metadata")
    val question: QuestionMetadataDto,
    @field:Schema(description = "Tags associated with the question")
    val tags: List<QuestionTagDto>,
    @field:Schema(description = "Related companies for this question")
    val companies: List<QuestionCompanyDto>,
    @field:Schema(description = "Related job roles for this question")
    val roles: List<QuestionRoleDto>,
    @field:Schema(description = "Recommended learning materials")
    val learningMaterials: List<LearningMaterialDto>,
    @field:Schema(description = "Curated model answers or answer outlines for this question")
    val referenceAnswers: List<QuestionReferenceAnswerDto> = emptyList(),
    @field:Schema(description = "Imported practical interview metadata when this question originated from a real interview asset", nullable = true)
    val practicalInterviewContext: PracticalInterviewQuestionContextDto? = null,
    @field:Schema(description = "Current user progress summary when the request is authenticated")
    val userProgressSummary: UserProgressSummaryDto?,
)
