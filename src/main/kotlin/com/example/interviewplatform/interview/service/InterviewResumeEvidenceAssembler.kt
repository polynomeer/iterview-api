package com.example.interviewplatform.interview.service

import com.example.interviewplatform.resume.repository.ResumeExperienceSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InterviewResumeEvidenceAssembler(
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeExperienceSnapshotRepository: ResumeExperienceSnapshotRepository,
) {
    @Transactional(readOnly = true)
    fun loadCandidates(resumeVersionId: Long, limit: Int = 8): List<InterviewResumeEvidenceCandidate> {
        val candidates = mutableListOf<InterviewResumeEvidenceCandidate>()

        resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId)
            .take(4)
            .mapNotNull { project ->
                val snippet = excerpt(project.contentText ?: project.summaryText.ifBlank { project.sourceText }) ?: return@mapNotNull null
                InterviewResumeEvidenceCandidate(
                    section = "project",
                    label = project.title.ifBlank { null },
                    snippet = snippet,
                    sourceRecordType = "resume_project_snapshot",
                    sourceRecordId = project.id,
                )
            }
            .also(candidates::addAll)

        resumeExperienceSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId)
            .take(3)
            .mapNotNull { experience ->
                val snippet = excerpt(experience.impactText ?: experience.summaryText.ifBlank { experience.sourceText }) ?: return@mapNotNull null
                InterviewResumeEvidenceCandidate(
                    section = "experience",
                    label = listOfNotNull(experience.companyName?.takeIf { it.isNotBlank() }, experience.roleName?.takeIf { it.isNotBlank() })
                        .joinToString(" - ")
                        .ifBlank { null },
                    snippet = snippet,
                    sourceRecordType = "resume_experience_snapshot",
                    sourceRecordId = experience.id,
                )
            }
            .also(candidates::addAll)

        return candidates.distinctBy { Triple(it.sourceRecordType, it.sourceRecordId, it.snippet) }.take(limit)
    }

    private fun excerpt(value: String?): String? {
        val normalized = value?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (normalized.isBlank()) {
            return null
        }
        return normalized.take(MAX_SNIPPET_LENGTH)
    }

    private companion object {
        const val MAX_SNIPPET_LENGTH = 220
    }
}
