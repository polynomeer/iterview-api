package com.example.interviewplatform.interview.service

import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.interview.entity.InterviewSessionEntity
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.interview.repository.InterviewRecordAnswerRepository
import com.example.interviewplatform.interview.repository.InterviewRecordQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewRecordRepository
import com.example.interviewplatform.interview.repository.InterviewerProfileRepository
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterviewFollowUpGenerationService(
    private val client: InterviewFollowUpGenerationClient,
    private val appLocaleService: AppLocaleService,
    private val interviewRecordRepository: InterviewRecordRepository,
    private val interviewRecordQuestionRepository: InterviewRecordQuestionRepository,
    private val interviewRecordAnswerRepository: InterviewRecordAnswerRepository,
    private val interviewerProfileRepository: InterviewerProfileRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val interviewResumeEvidenceAssembler: InterviewResumeEvidenceAssembler,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun generateResumeFollowUp(
        session: InterviewSessionEntity,
        answeredRow: InterviewSessionQuestionEntity,
        answerText: String,
        answerScore: Int,
        parentTags: List<String>,
        parentFocusSkillNames: List<String>,
        parentResumeEvidenceCandidates: List<InterviewResumeEvidenceCandidate>,
        preferredResumeEvidenceCandidates: List<InterviewResumeEvidenceCandidate>,
        usedFacetsForPreferredRecord: List<String>,
    ): GeneratedInterviewFollowUp? {
        val resumeVersionId = session.resumeVersionId ?: return null
        if (!client.isEnabled()) {
            return null
        }
        val version = resumeVersionRepository.findById(resumeVersionId).orElse(null) ?: return null
        val outputLanguage = appLocaleService.resolveLanguage()
        val answerQualitySignal = answerQualitySignal(answerScore)
        val preferredFollowUpStyle = preferredFollowUpStyle(answerScore)
        val input = InterviewFollowUpGenerationInput(
            outputLanguage = outputLanguage,
            answerQualitySignal = answerQualitySignal,
            preferredFollowUpStyle = preferredFollowUpStyle,
            parentPromptText = answeredRow.promptText ?: defaultParentPrompt(outputLanguage),
            parentBodyText = answeredRow.bodyText,
            answerText = answerText.trim(),
            resumeSummaryText = version.summaryText?.takeIf { it.isNotBlank() } ?: version.rawText?.take(1500),
            resumeSkillNames = resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(resumeVersionId)
                .map { it.skillName }
                .filter { it.isNotBlank() }
                .distinct()
                .take(8),
            resumeProjectSummaries = resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId)
                .mapNotNull { project ->
                    val title = project.title.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val category = project.projectCategoryName?.takeIf { it.isNotBlank() }
                    val summary = project.summaryText.takeIf { it.isNotBlank() }
                    listOfNotNull(title, category, summary).joinToString(" - ")
                }
                .take(4),
            resumeRiskSummaries = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(resumeVersionId)
                .map { "${it.title} (${it.severity}): ${it.description}" }
                .take(4),
            resumeEvidenceCandidates = interviewResumeEvidenceAssembler.loadCandidates(resumeVersionId),
            parentResumeEvidenceCandidates = parentResumeEvidenceCandidates,
            preferredResumeEvidenceCandidates = preferredResumeEvidenceCandidates,
            usedFacetsForPreferredRecord = usedFacetsForPreferredRecord,
            parentTags = parentTags,
            parentFocusSkillNames = parentFocusSkillNames,
        )
        return runCatching { client.generate(input) }.getOrNull()
    }

    @Transactional(readOnly = true)
    fun generateReplayFollowUp(
        session: InterviewSessionEntity,
        answeredRow: InterviewSessionQuestionEntity,
        answerText: String,
        answerScore: Int,
        parentTags: List<String>,
        parentFocusSkillNames: List<String>,
        sourceInterviewRecordId: Long,
    ): GeneratedInterviewFollowUp? {
        if (!client.isEnabled()) {
            return null
        }
        val record = interviewRecordRepository.findById(sourceInterviewRecordId).orElse(null) ?: return null
        val interviewerProfile = interviewerProfileRepository.findBySourceInterviewRecordId(sourceInterviewRecordId)
        val importedQuestions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(sourceInterviewRecordId)
        val answerByQuestionId = if (importedQuestions.isEmpty()) {
            emptyMap()
        } else {
            interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(importedQuestions.map { it.id })
                .associateBy { it.interviewRecordQuestionId }
        }
        val outputLanguage = appLocaleService.resolveLanguage()
        val input = InterviewFollowUpGenerationInput(
            outputLanguage = outputLanguage,
            answerQualitySignal = answerQualitySignal(answerScore),
            preferredFollowUpStyle = preferredFollowUpStyle(answerScore),
            parentPromptText = answeredRow.promptText ?: defaultParentPrompt(outputLanguage),
            parentBodyText = answeredRow.bodyText,
            answerText = answerText.trim(),
            resumeSummaryText = null,
            resumeSkillNames = emptyList(),
            resumeProjectSummaries = emptyList(),
            resumeRiskSummaries = emptyList(),
            resumeEvidenceCandidates = emptyList(),
            parentResumeEvidenceCandidates = emptyList(),
            preferredResumeEvidenceCandidates = emptyList(),
            usedFacetsForPreferredRecord = emptyList(),
            parentTags = parentTags,
            parentFocusSkillNames = parentFocusSkillNames,
            replayMode = session.replayMode,
            importedRecordSummary = record.overallSummary,
            interviewerToneProfile = interviewerProfile?.toneProfile,
            interviewerPressureLevel = interviewerProfile?.pressureLevel,
            interviewerDepthPreference = interviewerProfile?.depthPreference,
            interviewerStyleTags = interviewerProfile?.styleTagsJson?.let(::decodeJsonArray).orEmpty(),
            interviewerFavoriteTopics = interviewerProfile?.favoriteTopicsJson?.let(::decodeJsonArray).orEmpty(),
            interviewerFollowUpPatterns = interviewerProfile?.followUpPatternJson?.let(::decodeJsonArray).orEmpty(),
            importedQuestionExamples = importedQuestions.take(4).map { question ->
                val answerSummary = answerByQuestionId[question.id]?.summary
                buildString {
                    append("Q: ${question.text}")
                    if (!answerSummary.isNullOrBlank()) {
                        append(" | A summary: $answerSummary")
                    }
                    append(" | type=${question.questionType}")
                }
            },
        )
        return runCatching { client.generate(input) }.getOrNull()
    }

    private fun answerQualitySignal(answerScore: Int): String = when {
        answerScore >= STRONG_ANSWER_SCORE_THRESHOLD -> "strong"
        answerScore >= MID_ANSWER_SCORE_THRESHOLD -> "medium"
        else -> "weak"
    }

    private fun preferredFollowUpStyle(answerScore: Int): String = when {
        answerScore >= STRONG_ANSWER_SCORE_THRESHOLD -> "scenario_extension"
        answerScore >= MID_ANSWER_SCORE_THRESHOLD -> "technical_drill_down"
        else -> "evidence_challenge"
    }

    private fun defaultParentPrompt(language: String): String =
        if (language.lowercase() == "en") "Interview question" else "면접 질문"

    private fun decodeJsonArray(raw: String): List<String> =
        runCatching { objectMapper.readValue(raw, Array<String>::class.java).toList() }
            .getOrDefault(emptyList())

    private companion object {
        const val MID_ANSWER_SCORE_THRESHOLD = 70
        const val STRONG_ANSWER_SCORE_THRESHOLD = 85
    }
}
