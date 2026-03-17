package com.example.interviewplatform.resume.service

interface ResumeAnalysisGenerationClient {
    fun isEnabled(): Boolean

    fun generate(input: ResumeAnalysisGenerationInput): ResumeAnalysisGenerationResult
}

data class ResumeAnalysisGenerationInput(
    val companyName: String?,
    val roleName: String?,
    val resumeKeywords: List<String>,
    val postingKeywords: List<String>,
    val strongMatches: List<String>,
    val missingKeywords: List<String>,
    val weakSignals: List<String>,
    val recommendedFocusAreas: List<String>,
    val suggestedHeadline: String?,
    val suggestedSummary: String?,
    val recommendedFormatType: String,
    val suggestions: List<ResumeAnalysisSuggestionSeed>,
    val preferredSectionOrder: List<String>,
    val topExperienceTitles: List<String>,
    val topProjectTitles: List<String>,
)

data class ResumeAnalysisSuggestionSeed(
    val sectionKey: String,
    val originalText: String?,
    val suggestedText: String,
    val reason: String,
    val suggestionType: String,
)

data class ResumeAnalysisGenerationResult(
    val matchSummary: String,
    val suggestedHeadline: String?,
    val suggestedSummary: String?,
    val recommendedFormatType: String,
    val analysisNotes: List<String>,
    val diffSummary: String?,
    val suggestions: List<ResumeAnalysisSuggestionSeed>,
    val tailoredDocument: TailoredResumeDocument,
    val generationSource: String,
    val llmModel: String?,
)

data class TailoredResumeDocument(
    val title: String,
    val targetCompany: String?,
    val targetRole: String?,
    val formatType: String,
    val sectionOrder: List<String>,
    val summary: String?,
    val diffSummary: String?,
    val analysisNotes: List<String>,
    val sections: List<TailoredResumeSection>,
    val plainText: String,
)

data class TailoredResumeSection(
    val sectionKey: String,
    val title: String,
    val lines: List<String>,
)
