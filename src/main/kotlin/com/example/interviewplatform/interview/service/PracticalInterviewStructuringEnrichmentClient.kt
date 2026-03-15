package com.example.interviewplatform.interview.service

data class PracticalInterviewStructuringEnrichmentInput(
    val outputLanguage: String,
    val companyName: String?,
    val roleName: String?,
    val interviewType: String?,
    val transcriptText: String,
    val deterministicSummary: String?,
    val questions: List<PracticalInterviewStructuringQuestionInput>,
)

data class PracticalInterviewStructuringQuestionInput(
    val orderIndex: Int,
    val text: String,
    val questionType: String,
    val topicTags: List<String>,
    val intentTags: List<String>,
    val parentOrderIndex: Int?,
    val answerText: String?,
    val answerSummary: String?,
    val weaknessTags: List<String>,
    val strengthTags: List<String>,
)

data class PracticalInterviewStructuringEnrichment(
    val overallSummary: String?,
    val questions: List<PracticalInterviewStructuringQuestionEnrichment>,
    val interviewerProfile: PracticalInterviewInterviewerProfileOverride?,
)

data class PracticalInterviewStructuringQuestionEnrichment(
    val orderIndex: Int,
    val questionType: String?,
    val topicTags: List<String>?,
    val intentTags: List<String>?,
    val parentOrderIndex: Int?,
    val answerSummary: String?,
    val weaknessTags: List<String>?,
    val strengthTags: List<String>?,
    val confidenceMarkers: List<String>?,
    val analysis: Map<String, Any>?,
)

data class PracticalInterviewInterviewerProfileOverride(
    val styleTags: List<String>,
    val toneProfile: String?,
    val pressureLevel: String?,
    val depthPreference: String?,
    val followUpPatterns: List<String>,
    val favoriteTopics: List<String>,
    val openingPattern: String?,
    val closingPattern: String?,
)

interface PracticalInterviewStructuringEnrichmentClient {
    fun isEnabled(): Boolean

    fun enrich(input: PracticalInterviewStructuringEnrichmentInput): PracticalInterviewStructuringEnrichment
}
