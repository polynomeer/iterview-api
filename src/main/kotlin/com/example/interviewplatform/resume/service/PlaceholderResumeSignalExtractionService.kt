package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PlaceholderResumeSignalExtractionService(
    private val objectMapper: ObjectMapper,
) : ResumeSignalExtractionService {
    override fun extract(version: ResumeVersionEntity): ExtractedResumeSignals {
        val sections = ParsedResumeSections.parse(version)
        val experiences = extractExperiences(version, sections)
        val projects = extractProjects(sections, experiences)
        val achievements = extractAchievements(experiences, projects)
        return ExtractedResumeSignals(
            profile = extractProfile(sections),
            contacts = extractContacts(sections),
            competencies = extractCompetencies(sections),
            skills = extractSkills(version, sections),
            experiences = experiences,
            projects = projects,
            achievements = achievements,
            educationItems = extractEducation(sections),
            certificationItems = extractCertifications(sections),
            awardItems = extractAwards(sections),
            risks = extractRisks(version, experiences, achievements),
            sourceType = "deterministic",
            extractionStatus = "skipped",
            extractionErrorMessage = null,
            extractionConfidence = null,
            llmModel = null,
            llmPromptVersion = null,
            rawExtractionPayload = version.parsedJson,
        )
    }

    private fun extractProfile(sections: ParsedResumeSections): ExtractedResumeProfile? {
        val fullName = sections.lines.firstOrNull()
        val headline = sections.lines.firstOrNull { it.contains("Make Non Polynomial Polynomial") || it.startsWith("💡") }
            ?.substringAfter("💡", "")
            ?.trim()
        val summary = sections.summaryLines.takeIf { it.isNotEmpty() }?.joinToString(" ")
        return if (listOf(fullName, headline, summary).all { it.isNullOrBlank() }) {
            null
        } else {
            ExtractedResumeProfile(
                fullName = fullName,
                headline = headline,
                summaryText = summary,
                locationText = null,
                yearsOfExperienceText = null,
                sourceText = listOfNotNull(fullName, headline, summary).joinToString("\n"),
            )
        }
    }

    private fun extractContacts(sections: ParsedResumeSections): List<ExtractedResumeContactPoint> {
        val contactLine = sections.lines.take(5).joinToString(" ")
        return CONTACT_PATTERNS.mapNotNull { (type, regex) ->
            regex.find(contactLine)?.groupValues?.getOrNull(1)?.trim()?.let { value -> type to value }
        }.mapIndexed { index, (type, value) ->
            ExtractedResumeContactPoint(
                contactType = type,
                label = type,
                valueText = value.takeUnless { it.startsWith("http") },
                url = value.takeIf { it.startsWith("http") },
                displayOrder = index + 1,
                isPrimary = index == 0,
            )
        }
    }

    private fun extractCompetencies(sections: ParsedResumeSections): List<ExtractedResumeCompetency> =
        sections.competencyLines.mapIndexedNotNull { index, line ->
            val normalized = line.removePrefix("•").trim()
            normalized.takeIf { it.isNotBlank() }?.let {
                val title = it.substringBefore('.').takeIf { prefix -> prefix.length in 4..40 } ?: "Core competency ${index + 1}"
                ExtractedResumeCompetency(
                    title = title,
                    description = it,
                    sourceText = it,
                    displayOrder = index + 1,
                )
            }
        }

    private fun extractSkills(version: ResumeVersionEntity, sections: ParsedResumeSections): List<ExtractedResumeSkill> {
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

        val skillSectionText = sections.skillLines.joinToString(" ")
        val rawText = version.rawText.orEmpty()
        return KNOWN_SKILLS.filter { rawText.contains(it, ignoreCase = true) || skillSectionText.contains(it, ignoreCase = true) }.map {
            ExtractedResumeSkill(
                skillName = it,
                sourceText = skillSectionText.takeIf { text -> text.isNotBlank() } ?: rawText.take(200),
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

    private fun extractExperiences(
        version: ResumeVersionEntity,
        sections: ParsedResumeSections,
    ): List<ExtractedResumeExperience> {
        val grouped = sections.careerEntries.takeIf { it.isNotEmpty() } ?: fallbackExperienceGroups(version)
        return grouped.take(6).mapIndexed { index, entry ->
            val header = parseCareerHeader(entry.firstOrNull().orEmpty())
            val sourceText = entry.joinToString(" ")
            ExtractedResumeExperience(
                projectName = null,
                companyName = header.companyName,
                roleName = header.roleName,
                employmentType = null,
                startedOn = header.startedOn,
                endedOn = header.endedOn,
                isCurrent = header.isCurrent,
                summaryText = entry.drop(1).take(3).joinToString(" ").ifBlank { sourceText },
                impactText = entry.firstOrNull { line -> IMPACT_HINTS.any { hint -> line.contains(hint, ignoreCase = true) } || line.contains("→") },
                sourceText = sourceText,
                riskLevel = riskLevelFor(sourceText),
                displayOrder = index + 1,
            )
        }
    }

    private fun fallbackExperienceGroups(version: ResumeVersionEntity): List<List<String>> {
        val source = buildString {
            if (!version.summaryText.isNullOrBlank()) {
                appendLine(version.summaryText.trim())
            }
            if (!version.rawText.isNullOrBlank()) {
                appendLine(version.rawText.trim())
            }
        }.trim()
        return source.split('.', '\n')
            .map { it.trim() }
            .filter { it.length >= 20 }
            .take(3)
            .map { listOf(it) }
    }

    private fun extractProjects(
        sections: ParsedResumeSections,
        experiences: List<ExtractedResumeExperience>,
    ): List<ExtractedResumeProject> =
        sections.projectEntries.take(12).mapIndexed { index, entry ->
            val header = parseProjectHeader(entry.firstOrNull().orEmpty())
            val sourceText = entry.joinToString(" ")
            ExtractedResumeProject(
                title = header.title ?: "Project ${index + 1}",
                organizationName = experiences.getOrNull(index)?.companyName,
                roleName = null,
                summaryText = entry.drop(1).take(4).joinToString(" ").ifBlank { sourceText },
                techStackText = entry.firstOrNull { it.startsWith("기술 ") || it.startsWith("기술스택 ") },
                startedOn = header.startedOn,
                endedOn = header.endedOn,
                displayOrder = index + 1,
                sourceText = sourceText,
                experienceDisplayOrder = experiences.getOrNull(index)?.displayOrder,
            )
        }

    private fun extractAchievements(
        experiences: List<ExtractedResumeExperience>,
        projects: List<ExtractedResumeProject>,
    ): List<ExtractedResumeAchievement> {
        val sourceLines = buildList<AchievementSource> {
            experiences.forEach {
                add(
                    AchievementSource(
                        sourceText = it.sourceText,
                        experienceDisplayOrder = it.displayOrder,
                        projectDisplayOrder = null,
                    ),
                )
            }
            projects.forEach {
                add(
                    AchievementSource(
                        sourceText = it.sourceText ?: it.summaryText,
                        experienceDisplayOrder = it.experienceDisplayOrder,
                        projectDisplayOrder = it.displayOrder,
                    ),
                )
            }
        }
        return sourceLines
            .mapNotNull { source ->
                if (source.sourceText.contains("→") || source.sourceText.contains("%") || source.sourceText.contains("배") || source.sourceText.contains("건")) source else null
            }
            .flatMap { source ->
                source.sourceText
                    .split("•", "·", "\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && (it.contains("→") || METRIC_PATTERN.containsMatchIn(it)) }
                    .ifEmpty { listOf(source.sourceText) }
                    .map { line -> source to line }
            }
            .distinctBy { (_, line) -> line }
            .take(20)
            .mapIndexed { index, (source, line) ->
                val metric = METRIC_PATTERN.find(line)?.value
                ExtractedResumeAchievement(
                    title = line.substringBefore("→").substringBefore(":").trim().ifBlank { "Achievement ${index + 1}" },
                    metricText = metric,
                    impactSummary = line,
                    sourceText = line,
                    severityHint = if (metric != null) "high" else "medium",
                    displayOrder = index + 1,
                    experienceDisplayOrder = source.experienceDisplayOrder,
                    projectDisplayOrder = source.projectDisplayOrder,
                )
            }
    }

    private fun extractEducation(sections: ParsedResumeSections): List<ExtractedResumeEducation> =
        sections.educationLines.mapIndexedNotNull { index, line ->
            parseDatedEntry(line)?.let { dated ->
                ExtractedResumeEducation(
                    institutionName = dated.body.substringBefore("졸업").substringBefore("수료").trim(),
                    degreeName = dated.body.takeIf { it.contains("졸업") },
                    fieldOfStudy = dated.body.substringBefore("졸업").takeIf { it.contains("대학교") },
                    startedOn = dated.startedOn,
                    endedOn = dated.endedOn,
                    description = dated.body,
                    displayOrder = index + 1,
                    sourceText = line,
                )
            }
        }

    private fun extractCertifications(sections: ParsedResumeSections): List<ExtractedResumeCertification> =
        sections.certificationLines.mapIndexed { index, line ->
            val parts = line.removePrefix("•").split("|").map { it.trim() }
            ExtractedResumeCertification(
                name = parts.firstOrNull().orEmpty().substringBefore("(").trim(),
                issuerName = parts.getOrNull(1),
                credentialCode = CERT_CODE_PATTERN.find(line)?.value,
                issuedOn = parseSingleDate(line),
                expiresOn = null,
                scoreText = SCORE_PATTERN.find(line)?.value,
                displayOrder = index + 1,
                sourceText = line,
            )
        }

    private fun extractAwards(sections: ParsedResumeSections): List<ExtractedResumeAward> =
        sections.awardLines.mapIndexedNotNull { index, line ->
            parseSingleDate(line)?.let { awardedOn ->
                val body = line.removePrefix("•").trim().substringAfter(Regex("\\d{4}\\.\\d{1,2}").find(line)?.value ?: "").trim()
                ExtractedResumeAward(
                    title = body.substringBefore("(").trim(),
                    issuerName = body.substringAfterLast(" ").takeIf { it.isNotBlank() && it != body },
                    awardedOn = awardedOn,
                    description = body,
                    displayOrder = index + 1,
                    sourceText = line,
                )
            }
        }

    private fun extractRisks(
        version: ResumeVersionEntity,
        experiences: List<ExtractedResumeExperience>,
        achievements: List<ExtractedResumeAchievement>,
    ): List<ExtractedResumeRisk> = (experiences
        .filter { it.riskLevel == "high" || it.sourceText.contains('%') }
        .map {
            ExtractedResumeRisk(
                riskType = if (it.sourceText.contains('%')) "impact_claim" else "experience_claim",
                title = "Resume claim needs follow-up defense",
                description = "Be ready to defend this claim from resume version ${version.versionNo}: ${it.sourceText}",
                severity = it.riskLevel.uppercase(),
            )
        } + achievements
        .filter { it.metricText != null }
        .map {
            ExtractedResumeRisk(
                riskType = "achievement_claim",
                title = "Measured achievement needs evidence",
                description = "Be ready to explain the evidence and method behind: ${it.impactSummary}",
                severity = "HIGH",
            )
        }).distinctBy { it.riskType to it.description }

    private fun parseCareerHeader(line: String): CareerHeader {
        val normalized = line.trim()
        val rangeMatch = RANGE_PATTERN.find(normalized)
        val (startedOn, endedOn, isCurrent) = parseRange(rangeMatch?.value)
        val prefix = normalized.substringBefore(rangeMatch?.value ?: "").trim()
        val parts = prefix.split(" - ").map { it.trim() }
        return CareerHeader(
            companyName = parts.getOrNull(0),
            roleName = parts.getOrNull(1),
            startedOn = startedOn,
            endedOn = endedOn,
            isCurrent = isCurrent,
        )
    }

    private fun parseProjectHeader(line: String): ProjectHeader {
        val normalized = line.trim()
        val rangeMatch = RANGE_PATTERN.find(normalized)
        val (startedOn, endedOn, _) = parseRange(rangeMatch?.value)
        val title = normalized.substringBefore(rangeMatch?.value ?: "").trim().takeIf { it.isNotBlank() }
        return ProjectHeader(title = title, startedOn = startedOn, endedOn = endedOn)
    }

    private fun parseDatedEntry(line: String): DatedEntry? {
        val rangeMatch = RANGE_PATTERN.find(line) ?: return null
        val (startedOn, endedOn, _) = parseRange(rangeMatch.value)
        val body = line.substringAfter(rangeMatch.value).trim()
        return DatedEntry(startedOn, endedOn, body)
    }

    private fun parseSingleDate(line: String): LocalDate? =
        SINGLE_DATE_PATTERN.find(line)?.let { toLocalDate(it.value) }

    private fun parseRange(value: String?): Triple<LocalDate?, LocalDate?, Boolean> {
        if (value.isNullOrBlank()) return Triple(null, null, false)
        val parts = value.split("~").map { it.trim() }
        val started = parts.getOrNull(0)?.let(::toLocalDate)
        val endText = parts.getOrNull(1)
        val isCurrent = endText?.contains("현재") == true
        val ended = endText?.takeUnless { it.contains("현재") }?.let(::toLocalDate)
        return Triple(started, ended, isCurrent)
    }

    private fun toLocalDate(token: String): LocalDate? {
        val match = SINGLE_DATE_PATTERN.find(token) ?: return null
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toInt() ?: 1
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun riskLevelFor(sourceText: String): String = when {
        sourceText.contains('%') -> "high"
        RISK_HINTS.any { sourceText.contains(it, ignoreCase = true) } -> "medium"
        else -> "low"
    }

    private companion object {
        val KNOWN_SKILLS = listOf("Spring Boot", "Kotlin", "Java", "Go", "Python", "JPA", "QueryDSL", "MySQL", "Redis", "RabbitMQ", "SQS", "Docker", "AWS")
        val IMPACT_HINTS = listOf("improved", "reduced", "increased", "latency", "throughput", "개선", "단축")
        val RISK_HINTS = listOf("designed", "built", "scaled", "migrated", "introduced", "improved", "설계", "구축")
        val CONTACT_PATTERNS = listOf(
            "phone" to Regex("""Contact\s*:\s*([0-9\-+() ]+)"""),
            "blog" to Regex("""Blog\s*:\s*(https?://\S+)"""),
            "email" to Regex("""Mail\s*:\s*([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})"""),
            "github" to Regex("""GitHub\s*:\s*(https?://\S+)"""),
        )
        val RANGE_PATTERN = Regex("""(\d{4}\.\d{1,2}(?:\.\d{1,2})?)\s*~\s*(\d{4}\.\d{1,2}(?:\.\d{1,2})?|현재)""")
        val SINGLE_DATE_PATTERN = Regex("""(\d{4})\.(\d{1,2})(?:\.(\d{1,2}))?""")
        val METRIC_PATTERN = Regex("""\d+(?:\.\d+)?(?:%|배|건|GB|MB|초|분|시간)""")
        val CERT_CODE_PATTERN = Regex("""[A-Z0-9-]{5,}""")
        val SCORE_PATTERN = Regex("""\b\d{3,4}점\b""")
    }
}

private data class ParsedResumeSections(
    val lines: List<String>,
    val summaryLines: List<String>,
    val skillLines: List<String>,
    val competencyLines: List<String>,
    val educationLines: List<String>,
    val awardLines: List<String>,
    val certificationLines: List<String>,
    val careerEntries: List<List<String>>,
    val projectEntries: List<List<String>>,
) {
    companion object {
        private val ENTRY_RANGE_PATTERN = Regex("""(\d{4}\.\d{1,2}(?:\.\d{1,2})?)\s*~\s*(\d{4}\.\d{1,2}(?:\.\d{1,2})?|현재)""")

        fun parse(version: ResumeVersionEntity): ParsedResumeSections {
            val lines = version.rawText.orEmpty()
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val sections = linkedMapOf<String, MutableList<String>>()
            var current = "intro"
            sections[current] = mutableListOf()
            lines.forEach { line ->
                val nextSection = when {
                    line.contains("기술스택") -> "skills"
                    line.contains("보유 역량") -> "competencies"
                    line.contains("교육 및 활동") -> "education"
                    line.contains("수상이력") -> "awards"
                    line.contains("자격사항") -> "certifications"
                    line == "💼 경력" || line.contains(" 경력") -> "career"
                    line == "📜 프로젝트" || line.contains(" 프로젝트") -> "projects"
                    else -> null
                }
                if (nextSection != null) {
                    current = nextSection
                    sections.computeIfAbsent(current) { mutableListOf() }
                } else {
                    sections.computeIfAbsent(current) { mutableListOf() }.add(line)
                }
            }
            return ParsedResumeSections(
                lines = lines,
                summaryLines = sections["intro"].orEmpty().drop(5).take(6),
                skillLines = sections["skills"].orEmpty(),
                competencyLines = sections["competencies"].orEmpty(),
                educationLines = sections["education"].orEmpty().filter { it.startsWith("•") },
                awardLines = sections["awards"].orEmpty().filter { it.startsWith("•") },
                certificationLines = sections["certifications"].orEmpty().filter { it.startsWith("•") },
                careerEntries = groupCareerEntries(sections["career"].orEmpty()),
                projectEntries = groupProjectEntries(sections["projects"].orEmpty()),
            )
        }

        private fun groupCareerEntries(lines: List<String>): List<List<String>> {
            val results = mutableListOf<MutableList<String>>()
            lines.forEach { line ->
                if (line.contains(" - ") && ENTRY_RANGE_PATTERN.containsMatchIn(line)) {
                    results.add(mutableListOf(line))
                } else if (results.isNotEmpty()) {
                    results.last().add(line)
                }
            }
            return results
        }

        private fun groupProjectEntries(lines: List<String>): List<List<String>> {
            val results = mutableListOf<MutableList<String>>()
            lines.forEach { line ->
                if (!line.startsWith("문제") && !line.startsWith("개선") && !line.startsWith("성과") && ENTRY_RANGE_PATTERN.containsMatchIn(line)) {
                    results.add(mutableListOf(line))
                } else if (results.isNotEmpty()) {
                    results.last().add(line)
                }
            }
            return results
        }
    }
}

private data class CareerHeader(
    val companyName: String?,
    val roleName: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val isCurrent: Boolean,
)

private data class ProjectHeader(
    val title: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
)

private data class DatedEntry(
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val body: String,
)

private data class AchievementSource(
    val sourceText: String,
    val experienceDisplayOrder: Int?,
    val projectDisplayOrder: Int?,
)
