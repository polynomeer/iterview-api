package com.example.interviewplatform.interview.service

data class InterviewOpeningGenerationInput(
    val outputLanguage: String,
    val resumeSummaryText: String?,
    val resumeSkillNames: List<String>,
    val resumeProjectSummaries: List<String>,
    val resumeRiskSummaries: List<String>,
    val resumeEvidenceCandidates: List<InterviewResumeEvidenceCandidate>,
    val preferredResumeEvidenceCandidates: List<InterviewResumeEvidenceCandidate> = emptyList(),
)

data class GeneratedInterviewOpening(
    val promptText: String,
    val bodyText: String?,
    val tags: List<String>,
    val focusSkillNames: List<String>,
    val resumeContextSummary: String?,
    val resumeEvidence: List<GeneratedInterviewResumeEvidence>,
    val generationRationale: String,
    val llmModel: String?,
    val llmPromptVersion: String?,
    val contentLocale: String,
)

interface InterviewOpeningGenerationClient {
    fun isEnabled(): Boolean

    fun generate(input: InterviewOpeningGenerationInput): GeneratedInterviewOpening
}
