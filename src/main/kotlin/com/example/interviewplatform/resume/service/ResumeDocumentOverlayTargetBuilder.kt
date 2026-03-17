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
            targets += buildPhraseTargets(
                resumeVersionId = resumeVersionId,
                anchorType = anchorType,
                anchorRecordId = anchorRecordId,
                anchorKey = anchorKey,
                fieldPath = fieldPath,
                sentence = segment,
                now = now,
                displayOrderStart = displayOrderStart + targets.size,
            )
            targets += buildKeywordTargets(
                resumeVersionId = resumeVersionId,
                anchorType = anchorType,
                anchorRecordId = anchorRecordId,
                anchorKey = anchorKey,
                fieldPath = fieldPath,
                sentence = segment,
                now = now,
                displayOrderStart = displayOrderStart + targets.size,
            )
        }
        return targets
    }

    private fun buildPhraseTargets(
        resumeVersionId: Long,
        anchorType: String,
        anchorRecordId: Long?,
        anchorKey: String?,
        fieldPath: String,
        sentence: SentenceSegment,
        now: Instant,
        displayOrderStart: Int,
    ): List<ResumeDocumentOverlayTargetEntity> {
        val phrases = sentence.text
            .split(phraseSeparator)
            .map { it.trim() }
            .filter { it.length >= 8 && it != sentence.text }
            .distinct()
        if (phrases.isEmpty()) {
            return emptyList()
        }
        var searchOffset = 0
        return phrases.mapIndexed { index, phrase ->
            val localStart = sentence.text.indexOf(phrase, searchOffset).takeIf { it >= 0 } ?: searchOffset
            searchOffset = localStart + phrase.length
            ResumeDocumentOverlayTargetEntity(
                resumeVersionId = resumeVersionId,
                anchorType = anchorType,
                anchorRecordId = anchorRecordId,
                anchorKey = anchorKey,
                targetType = "phrase",
                fieldPath = fieldPath,
                textSnippet = phrase,
                textStartOffset = sentence.startOffset + localStart,
                textEndOffset = sentence.startOffset + localStart + phrase.length,
                sentenceIndex = sentence.sentenceIndex,
                paragraphIndex = sentence.paragraphIndex,
                displayOrder = displayOrderStart + index,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private fun buildKeywordTargets(
        resumeVersionId: Long,
        anchorType: String,
        anchorRecordId: Long?,
        anchorKey: String?,
        fieldPath: String,
        sentence: SentenceSegment,
        now: Instant,
        displayOrderStart: Int,
    ): List<ResumeDocumentOverlayTargetEntity> {
        val keywords = extractKeywords(sentence.text, fieldPath)
        if (keywords.isEmpty()) {
            return emptyList()
        }
        var searchOffset = 0
        return keywords.mapIndexed { index, keyword ->
            val localStart = sentence.text.indexOf(keyword, searchOffset, ignoreCase = true).takeIf { it >= 0 }
                ?: sentence.text.indexOf(keyword, ignoreCase = true).takeIf { it >= 0 }
                ?: 0
            searchOffset = localStart + keyword.length
            ResumeDocumentOverlayTargetEntity(
                resumeVersionId = resumeVersionId,
                anchorType = anchorType,
                anchorRecordId = anchorRecordId,
                anchorKey = anchorKey,
                targetType = "keyword",
                fieldPath = fieldPath,
                textSnippet = keyword,
                textStartOffset = sentence.startOffset + localStart,
                textEndOffset = sentence.startOffset + localStart + keyword.length,
                sentenceIndex = sentence.sentenceIndex,
                paragraphIndex = sentence.paragraphIndex,
                displayOrder = displayOrderStart + index,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private fun extractKeywords(text: String, fieldPath: String): List<String> {
        val delimiterKeywords = text.split(keywordSeparator)
            .map { it.trim() }
            .filter { it.length in 2..30 }
        val tokenKeywords = keywordTokenRegex.findAll(text)
            .map { it.value.trim() }
            .filter { it.length in 2..24 }
            .filter { candidate ->
                fieldPath.contains("techStack", ignoreCase = true) ||
                    fieldPath.contains("skill", ignoreCase = true) ||
                    candidate.any { it.isUpperCase() } ||
                    candidate.any { it.isDigit() }
            }
            .toList()
        return (delimiterKeywords + tokenKeywords)
            .map { it.removePrefix("기술스택 ").trim() }
            .filter { it.isNotBlank() && it != text.trim() }
            .distinct()
            .take(8)
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
            candidates.forEachIndexed { sentenceIndex, candidate ->
                val startInParagraph = trimmedParagraph.indexOf(candidate, startIndex = localOffset).takeIf { it >= 0 } ?: localOffset
                val startOffset = paragraphStart + startInParagraph
                val endOffset = startOffset + candidate.length
                segments += SentenceSegment(
                    text = candidate,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    sentenceIndex = sentenceIndex,
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
        private val phraseSeparator = Regex("\\s*,\\s*|\\s+및\\s+|\\s+and\\s+")
        private val keywordSeparator = Regex("\\s*,\\s*|/|·|\\|")
        private val keywordTokenRegex = Regex("[A-Za-z][A-Za-z0-9+#.\\-]{1,23}")
    }
}

private data class SentenceSegment(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val sentenceIndex: Int,
    val paragraphIndex: Int,
)
