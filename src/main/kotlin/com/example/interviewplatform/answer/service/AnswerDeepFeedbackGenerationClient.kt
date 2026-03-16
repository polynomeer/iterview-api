package com.example.interviewplatform.answer.service

data class AnswerDeepFeedbackGenerationInput(
    val outputLanguage: String,
    val questionTitle: String,
    val questionBody: String?,
    val answerText: String,
    val answerMode: String,
    val totalScore: Int,
    val structureScore: Int,
    val specificityScore: Int,
    val technicalAccuracyScore: Int,
    val feedbackTitles: List<String>,
    val feedbackBodies: List<String>,
)

data class GeneratedAnswerDeepFeedback(
    val detailedFeedback: String,
    val strengthPoints: List<String>,
    val improvementPoints: List<String>,
    val missedPoints: List<String>,
    val modelAnswerText: String,
    val llmModel: String?,
    val contentLocale: String,
)

interface AnswerDeepFeedbackGenerationClient {
    fun isEnabled(): Boolean

    fun generate(input: AnswerDeepFeedbackGenerationInput): GeneratedAnswerDeepFeedback
}
