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
            .take(6)
            .flatMap { project ->
                projectSnippets(project.summaryText, project.contentText, project.sourceText).map { snippet ->
                    InterviewResumeEvidenceCandidate(
                        section = "project",
                        label = project.title.ifBlank { null },
                        snippet = snippet,
                        sourceRecordType = "resume_project_snapshot",
                        sourceRecordId = project.id,
                    )
                }
            }
            .also(candidates::addAll)

        resumeExperienceSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(resumeVersionId)
            .take(4)
            .flatMap { experience ->
                experienceSnippets(experience.summaryText, experience.impactText, experience.sourceText).map { snippet ->
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
            }
            .also(candidates::addAll)

        return candidates.distinctBy { Triple(it.sourceRecordType, it.sourceRecordId, it.snippet) }.take(limit)
    }

    private fun projectSnippets(summaryText: String?, contentText: String?, sourceText: String?): List<String> =
        buildEvidenceSnippets(
            primaryText = summaryText,
            detailedText = contentText,
            fallbackText = sourceText,
        )

    private fun experienceSnippets(summaryText: String?, impactText: String?, sourceText: String?): List<String> =
        buildEvidenceSnippets(
            primaryText = summaryText,
            detailedText = impactText,
            fallbackText = sourceText,
        )

    private fun buildEvidenceSnippets(
        primaryText: String?,
        detailedText: String?,
        fallbackText: String?,
    ): List<String> {
        val ordered = linkedSetOf<String>()
        excerpt(primaryText)?.let(ordered::add)
        extractDetailedSnippets(detailedText).forEach(ordered::add)
        if (ordered.isEmpty()) {
            extractDetailedSnippets(fallbackText).forEach(ordered::add)
        }
        if (ordered.isEmpty()) {
            excerpt(fallbackText)?.let(ordered::add)
        }
        return ordered.take(MAX_SNIPPETS_PER_RECORD)
    }

    private fun extractDetailedSnippets(value: String?): List<String> {
        val normalized = normalize(value)
        if (normalized.isBlank()) {
            return emptyList()
        }
        val bySentence = normalized
            .split(Regex("(?<=[.!?])\\s+|\\s*[\\n\\r]+\\s*"))
            .flatMap { sentence ->
                sentence.split(Regex("\\s*[;•·]\\s*|,\\s+"))
            }
            .mapNotNull(::excerpt)
            .distinct()
        return if (bySentence.isNotEmpty()) bySentence else listOfNotNull(excerpt(normalized))
    }

    private fun excerpt(value: String?): String? {
        val normalized = normalize(value)
        if (normalized.isBlank()) {
            return null
        }
        return normalized.take(MAX_SNIPPET_LENGTH)
    }

    private fun normalize(value: String?): String = value?.replace(Regex("\\s+"), " ")?.trim().orEmpty()

    private companion object {
        const val MAX_SNIPPET_LENGTH = 220
        const val MAX_SNIPPETS_PER_RECORD = 4
    }
}
