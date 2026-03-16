package com.example.interviewplatform.question.service

data class QuestionReferenceContentGenerationInput(
    val outputLanguage: String,
    val questionTitle: String,
    val questionBody: String?,
    val questionType: String,
    val difficultyLevel: String,
    val categoryName: String?,
    val tags: List<String>,
)

data class GeneratedQuestionReferenceAnswer(
    val title: String,
    val answerText: String,
    val answerFormat: String,
    val displayOrder: Int,
)

data class GeneratedQuestionLearningMaterial(
    val title: String,
    val materialType: String,
    val description: String?,
    val contentText: String?,
    val contentUrl: String?,
    val difficultyLevel: String?,
    val estimatedMinutes: Int?,
    val relationshipType: String?,
    val displayOrder: Int,
    val relevanceScore: Double?,
)

data class GeneratedQuestionReferenceContent(
    val referenceAnswers: List<GeneratedQuestionReferenceAnswer>,
    val learningMaterials: List<GeneratedQuestionLearningMaterial>,
    val llmModel: String?,
    val contentLocale: String,
)

interface QuestionReferenceContentGenerationClient {
    fun isEnabled(): Boolean

    fun generate(input: QuestionReferenceContentGenerationInput): GeneratedQuestionReferenceContent
}
