package com.example.interviewplatform.interview.service

import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterviewOpeningGenerationService(
    private val client: InterviewOpeningGenerationClient,
    private val appLocaleService: AppLocaleService,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val interviewResumeEvidenceAssembler: InterviewResumeEvidenceAssembler,
) {
    @Transactional(readOnly = true)
    fun generateResumeOpening(
        resumeVersionId: Long,
        preferredEvidenceCandidates: List<InterviewResumeEvidenceCandidate> = emptyList(),
        preferredEvidenceRecoveryStatus: String? = null,
        preferredOpeningStyle: String? = null,
    ): GeneratedInterviewOpening? {
        if (!client.isEnabled()) {
            return null
        }
        val version = resumeVersionRepository.findById(resumeVersionId).orElse(null) ?: return null
        val outputLanguage = appLocaleService.resolveLanguage()
        val input = InterviewOpeningGenerationInput(
            outputLanguage = outputLanguage,
            resumeSummaryText = version.summaryText?.takeIf { it.isNotBlank() } ?: version.rawText?.take(1500),
            resumeSkillNames = resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(resumeVersionId)
                .map { it.skillName }
                .filter { it.isNotBlank() }
                .distinct()
                .take(10),
            resumeProjectSummaries = resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId)
                .mapNotNull { project ->
                    val title = project.title.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val category = project.projectCategoryName?.takeIf { it.isNotBlank() }
                    val summary = project.summaryText.takeIf { it.isNotBlank() }
                    listOfNotNull(title, category, summary).joinToString(" - ")
                }
                .take(5),
            resumeRiskSummaries = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(resumeVersionId)
                .map { "${it.title} (${it.severity}): ${it.description}" }
                .take(5),
            resumeEvidenceCandidates = interviewResumeEvidenceAssembler.loadCandidates(resumeVersionId),
            preferredResumeEvidenceCandidates = preferredEvidenceCandidates,
            preferredEvidenceRecoveryStatus = preferredEvidenceRecoveryStatus,
            preferredOpeningStyle = preferredOpeningStyle,
        )
        return runCatching { client.generate(input) }.getOrNull()
    }
}
