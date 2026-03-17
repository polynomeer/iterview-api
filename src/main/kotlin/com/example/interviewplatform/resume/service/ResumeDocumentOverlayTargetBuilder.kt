package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeCompetencyItemEntity
import com.example.interviewplatform.resume.entity.ResumeDocumentOverlayTargetEntity
import com.example.interviewplatform.resume.entity.ResumeExperienceSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeProfileSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeProjectSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeSkillSnapshotEntity
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ResumeDocumentOverlayTargetBuilder {
    fun buildForVersion(
        resumeVersionId: Long,
        profile: ResumeProfileSnapshotEntity?,
        competencies: List<ResumeCompetencyItemEntity>,
        skills: List<ResumeSkillSnapshotEntity>,
        experiences: List<ResumeExperienceSnapshotEntity>,
        projects: List<ResumeProjectSnapshotEntity>,
        now: Instant,
    ): List<ResumeDocumentOverlayTargetEntity> {
        val targets = mutableListOf<ResumeDocumentOverlayTargetEntity>()
        var displayOrder = 0

        profile?.summaryText?.takeIf { it.isNotBlank() }?.let { summaryText ->
            val built = buildTargetsForText(
                resumeVersionId = resumeVersionId,
                anchorType = "summary",
                anchorRecordId = null,
                anchorKey = "summary",
                fieldPath = "profile.summaryText",
                text = summaryText,
                now = now,
                displayOrderStart = displayOrder,
            )
            targets += built
            displayOrder += built.size
        }

        competencies.forEach { competency ->
            val text = listOf(competency.title, competency.description).filter { it.isNotBlank() }.joinToString(": ")
            if (text.isNotBlank()) {
                val built = buildTargetsForText(
                    resumeVersionId = resumeVersionId,
                    anchorType = "competency",
                    anchorRecordId = competency.id,
                    anchorKey = null,
                    fieldPath = "competency.description",
                    text = text,
                    now = now,
                    displayOrderStart = displayOrder,
                )
                targets += built
                displayOrder += built.size
            }
        }

        skills.forEach { skill ->
            val built = buildTargetsForText(
                resumeVersionId = resumeVersionId,
                anchorType = "skill",
                anchorRecordId = skill.id,
                anchorKey = null,
                fieldPath = "skill.skillName",
                text = skill.skillName,
                now = now,
                displayOrderStart = displayOrder,
            )
            targets += built
            displayOrder += built.size
        }

        experiences.forEach { experience ->
            val fields = buildList {
                add("experience.summaryText" to experience.summaryText)
                experience.impactText?.let { add("experience.impactText" to it) }
                add("experience.sourceText" to experience.sourceText)
            }
            fields.forEach { (fieldPath, text) ->
                val built = buildTargetsForText(
                    resumeVersionId = resumeVersionId,
                    anchorType = "experience",
                    anchorRecordId = experience.id,
                    anchorKey = null,
                    fieldPath = fieldPath,
                    text = text,
                    now = now,
                    displayOrderStart = displayOrder,
                )
                targets += built
                displayOrder += built.size
            }
        }

        projects.forEach { project ->
            val fields = buildList {
                add("project.summaryText" to project.summaryText)
                project.contentText?.let { add("project.contentText" to it) }
                project.techStackText?.let { add("project.techStackText" to it) }
                project.sourceText?.let { add("project.sourceText" to it) }
            }
            fields.forEach { (fieldPath, text) ->
                val built = buildTargetsForText(
                    resumeVersionId = resumeVersionId,
                    anchorType = "project",
                    anchorRecordId = project.id,
                    anchorKey = null,
                    fieldPath = fieldPath,
                    text = text,
                    now = now,
                    displayOrderStart = displayOrder,
                )
                targets += built
                displayOrder += built.size
            }
        }

        return targets
    }

    private fun buildTargetsForText(
        resumeVersionId: Long,
        anchorType: String,
        anchorRecordId: Long?,
        anchorKey: String?,
        fieldPath: String,
        text: String,
        now: Instant,
        displayOrderStart: Int,
    ): List<ResumeDocumentOverlayTargetEntity> {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return emptyList()
        }

        val targets = mutableListOf<ResumeDocumentOverlayTargetEntity>()
        targets += ResumeDocumentOverlayTargetEntity(
            resumeVersionId = resumeVersionId,
            anchorType = anchorType,
            anchorRecordId = anchorRecordId,
            anchorKey = anchorKey,
            targetType = "block",
            fieldPath = fieldPath,
            textSnippet = normalized,
            textStartOffset = 0,
            textEndOffset = normalized.length,
            sentenceIndex = null,
            paragraphIndex = null,
            displayOrder = displayOrderStart,
            createdAt = now,
            updatedAt = now,
        )

        splitIntoSentences(normalized).forEachIndexed { index, segment ->
            targets += ResumeDocumentOverlayTargetEntity(
                resumeVersionId = resumeVersionId,
                anchorType = anchorType,
                anchorRecordId = anchorRecordId,
                anchorKey = anchorKey,
                targetType = "sentence",
                fieldPath = fieldPath,
                textSnippet = segment.text,
                textStartOffset = segment.startOffset,
                textEndOffset = segment.endOffset,
                sentenceIndex = index,
                paragraphIndex = segment.paragraphIndex,
                displayOrder = displayOrderStart + index + 1,
                createdAt = now,
                updatedAt = now,
            )
        }
        return targets
    }

    private fun splitIntoSentences(text: String): List<SentenceSegment> {
        val segments = mutableListOf<SentenceSegment>()
        val paragraphs = text.split(paragraphSeparator).filter { it.isNotBlank() }
        var searchOffset = 0
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val trimmedParagraph = paragraph.trim()
            if (trimmedParagraph.isBlank()) {
                return@forEachIndexed
            }
            val paragraphStart = text.indexOf(trimmedParagraph, startIndex = searchOffset).takeIf { it >= 0 } ?: searchOffset
            val candidates = sentenceSeparator
                .split(trimmedParagraph)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(trimmedParagraph) }
            var localOffset = 0
            candidates.forEach { candidate ->
                val startInParagraph = trimmedParagraph.indexOf(candidate, startIndex = localOffset).takeIf { it >= 0 } ?: localOffset
                val startOffset = paragraphStart + startInParagraph
                val endOffset = startOffset + candidate.length
                segments += SentenceSegment(
                    text = candidate,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    paragraphIndex = paragraphIndex,
                )
                localOffset = startInParagraph + candidate.length
            }
            searchOffset = paragraphStart + trimmedParagraph.length
        }
        return segments
    }

    private companion object {
        private val paragraphSeparator = Regex("\\n\\s*\\n")
        private val sentenceSeparator = Regex("(?<=[.!?])\\s+|\\n")
    }
}

private data class SentenceSegment(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val paragraphIndex: Int,
)
