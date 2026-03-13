package com.example.interviewplatform.interview.service

data class InterviewFollowUpGenerationInput(
    val parentPromptText: String,
    val parentBodyText: String?,
    val answerText: String,
    val resumeSummaryText: String?,
    val resumeSkillNames: List<String>,
    val resumeProjectSummaries: List<String>,
    val resumeRiskSummaries: List<String>,
    val resumeEvidenceCandidates: List<InterviewResumeEvidenceCandidate>,
    val parentTags: List<String>,
    val parentFocusSkillNames: List<String>,
)

data class GeneratedInterviewFollowUp(
    val promptText: String,
    val bodyText: String?,
    val tags: List<String>,
    val focusSkillNames: List<String>,
    val resumeContextSummary: String?,
    val resumeEvidence: List<GeneratedInterviewResumeEvidence>,
    val generationRationale: String,
    val llmModel: String?,
    val llmPromptVersion: String?,
)

interface InterviewFollowUpGenerationClient {
    fun isEnabled(): Boolean

    fun generate(input: InterviewFollowUpGenerationInput): GeneratedInterviewFollowUp
}
