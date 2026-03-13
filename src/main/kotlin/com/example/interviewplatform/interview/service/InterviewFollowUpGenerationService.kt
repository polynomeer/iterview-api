package com.example.interviewplatform.interview.service

import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.interview.entity.InterviewSessionEntity
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterviewFollowUpGenerationService(
    private val client: InterviewFollowUpGenerationClient,
    private val appLocaleService: AppLocaleService,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val interviewResumeEvidenceAssembler: InterviewResumeEvidenceAssembler,
) {
    @Transactional(readOnly = true)
    fun generateResumeFollowUp(
        session: InterviewSessionEntity,
        answeredRow: InterviewSessionQuestionEntity,
        answerText: String,
        parentTags: List<String>,
        parentFocusSkillNames: List<String>,
    ): GeneratedInterviewFollowUp? {
        val resumeVersionId = session.resumeVersionId ?: return null
        if (!client.isEnabled()) {
            return null
        }
        val version = resumeVersionRepository.findById(resumeVersionId).orElse(null) ?: return null
        val outputLanguage = appLocaleService.resolveLanguage()
        val input = InterviewFollowUpGenerationInput(
            outputLanguage = outputLanguage,
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
            parentTags = parentTags,
            parentFocusSkillNames = parentFocusSkillNames,
        )
        return runCatching { client.generate(input) }.getOrNull()
    }

    private fun defaultParentPrompt(language: String): String =
        if (language.lowercase() == "en") "Interview question" else "면접 질문"
}
