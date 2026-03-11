package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class PlaceholderResumeSignalExtractionService(
    private val objectMapper: ObjectMapper,
) : ResumeSignalExtractionService {
    override fun extract(version: ResumeVersionEntity): ExtractedResumeSignals {
        val skills = extractSkills(version)
        val experiences = extractExperiences(version)
        val risks = extractRisks(version, experiences)
        return ExtractedResumeSignals(skills = skills, experiences = experiences, risks = risks)
    }

    private fun extractSkills(version: ResumeVersionEntity): List<ExtractedResumeSkill> {
        val parsedSkills = parseSkillNames(version.parsedJson)
        if (parsedSkills.isNotEmpty()) {
            return parsedSkills.map {
                ExtractedResumeSkill(
                    skillName = it,
                    sourceText = version.summaryText ?: version.rawText?.take(200),
                    confidenceScore = 0.9,
                )
            }
        }

        val rawText = version.rawText.orEmpty()
        return KNOWN_SKILLS.filter { rawText.contains(it, ignoreCase = true) }.map {
            ExtractedResumeSkill(
                skillName = it,
                sourceText = rawText.take(200),
                confidenceScore = 0.7,
            )
        }
    }

    private fun parseSkillNames(parsedJson: String?): List<String> {
        if (parsedJson.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val root = objectMapper.readTree(parsedJson)
            collectSkillNames(root).distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun collectSkillNames(node: JsonNode): List<String> {
        if (node.isArray) {
            return node.mapNotNull { child -> child.asText().trim().takeIf { it.isNotEmpty() } }
        }
        return node.get("skills")?.let(::collectSkillNames).orEmpty()
    }

    private fun extractExperiences(version: ResumeVersionEntity): List<ExtractedResumeExperience> {
        val source = buildString {
            if (!version.summaryText.isNullOrBlank()) {
                append(version.summaryText.trim())
                append(". ")
            }
            if (!version.rawText.isNullOrBlank()) {
                append(version.rawText.trim())
            }
        }.trim()

        if (source.isBlank()) {
            return emptyList()
        }

        return source.split('.', '\n')
            .map { it.trim() }
            .filter { it.length >= 20 }
            .take(3)
            .mapIndexed { index, segment ->
                ExtractedResumeExperience(
                    projectName = null,
                    summaryText = segment,
                    impactText = segment.takeIf { IMPACT_HINTS.any { hint -> segment.contains(hint, ignoreCase = true) } },
                    sourceText = segment,
                    riskLevel = riskLevelFor(segment),
                    displayOrder = index + 1,
                )
            }
    }

    private fun extractRisks(
        version: ResumeVersionEntity,
        experiences: List<ExtractedResumeExperience>,
    ): List<ExtractedResumeRisk> = experiences
        .filter { it.riskLevel == "high" || it.sourceText.contains('%') }
        .map {
            ExtractedResumeRisk(
                riskType = if (it.sourceText.contains('%')) "impact_claim" else "experience_claim",
                title = "Resume claim needs follow-up defense",
                description = "Be ready to defend this claim from resume version ${version.versionNo}: ${it.sourceText}",
                severity = it.riskLevel.uppercase(),
            )
        }

    private fun riskLevelFor(sourceText: String): String = when {
        sourceText.contains('%') -> "high"
        RISK_HINTS.any { sourceText.contains(it, ignoreCase = true) } -> "medium"
        else -> "low"
    }

    private companion object {
        val KNOWN_SKILLS = listOf("Spring Boot", "Kotlin", "PostgreSQL", "Redis", "Kafka", "REST API")
        val IMPACT_HINTS = listOf("improved", "reduced", "increased", "latency", "throughput")
        val RISK_HINTS = listOf("designed", "built", "scaled", "migrated", "introduced", "improved")
    }
}
