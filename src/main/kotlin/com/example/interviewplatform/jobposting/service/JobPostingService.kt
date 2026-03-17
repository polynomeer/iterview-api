package com.example.interviewplatform.jobposting.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.jobposting.dto.CreateJobPostingRequest
import com.example.interviewplatform.jobposting.dto.JobPostingDto
import com.example.interviewplatform.jobposting.entity.JobPostingEntity
import com.example.interviewplatform.jobposting.mapper.JobPostingMapper
import com.example.interviewplatform.jobposting.repository.JobPostingRepository
import com.example.interviewplatform.skill.repository.SkillRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@Service
class JobPostingService(
    private val jobPostingRepository: JobPostingRepository,
    private val skillRepository: SkillRepository,
    private val objectMapper: ObjectMapper,
    private val clockService: ClockService,
) {
    @Transactional(readOnly = true)
    fun listJobPostings(userId: Long): List<JobPostingDto> =
        jobPostingRepository.findByUserIdOrderByCreatedAtDesc(userId).map(::toDto)

    @Transactional(readOnly = true)
    fun getJobPosting(userId: Long, jobPostingId: Long): JobPostingDto =
        toDto(requireOwnedJobPosting(userId, jobPostingId))

    @Transactional
    fun createJobPosting(userId: Long, request: CreateJobPostingRequest): JobPostingDto {
        val normalizedInputType = request.inputType.trim().lowercase()
        if (normalizedInputType !in supportedInputTypes) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported job posting inputType: ${request.inputType}")
        }
        val rawText = request.rawText?.trim()?.takeIf { it.isNotEmpty() }
        val sourceUrl = request.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (rawText == null && sourceUrl == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Either rawText or sourceUrl is required")
        }
        val parsed = parseJobPosting(
            rawText = rawText,
            sourceUrl = sourceUrl,
            companyName = request.companyName?.trim()?.takeIf { it.isNotEmpty() },
            roleName = request.roleName?.trim()?.takeIf { it.isNotEmpty() },
        )
        val now = clockService.now()
        val saved = jobPostingRepository.save(
            JobPostingEntity(
                userId = userId,
                inputType = normalizedInputType,
                sourceUrl = sourceUrl,
                rawText = rawText,
                companyName = parsed.companyName,
                roleName = parsed.roleName,
                parsedRequirementsJson = objectMapper.writeValueAsString(parsed.requirements),
                parsedNiceToHaveJson = objectMapper.writeValueAsString(parsed.niceToHave),
                parsedKeywordsJson = objectMapper.writeValueAsString(parsed.keywords),
                parsedResponsibilitiesJson = objectMapper.writeValueAsString(parsed.responsibilities),
                parsedSummary = parsed.summary,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return JobPostingMapper.toDto(saved, parsed.requirements, parsed.niceToHave, parsed.keywords, parsed.responsibilities)
    }

    fun requireOwnedJobPosting(userId: Long, jobPostingId: Long): JobPostingEntity =
        jobPostingRepository.findByIdAndUserId(jobPostingId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found: $jobPostingId")

    private fun toDto(entity: JobPostingEntity): JobPostingDto =
        JobPostingMapper.toDto(
            entity = entity,
            parsedRequirements = decodeStringList(entity.parsedRequirementsJson),
            parsedNiceToHave = decodeStringList(entity.parsedNiceToHaveJson),
            parsedKeywords = decodeStringList(entity.parsedKeywordsJson),
            parsedResponsibilities = decodeStringList(entity.parsedResponsibilitiesJson),
        )

    private fun parseJobPosting(
        rawText: String?,
        sourceUrl: String?,
        companyName: String?,
        roleName: String?,
    ): ParsedJobPosting {
        val normalizedText = rawText.orEmpty()
        val lines = normalizedText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lowerLines = lines.map { it.lowercase() }
        val allSkills = skillRepository.findAll()
        val detectedSkills = allSkills
            .map { it.name }
            .filter { skillName -> normalizedText.contains(skillName, ignoreCase = true) }
        val keywordTokens = Regex("""[A-Za-z][A-Za-z0-9+.#/-]{1,}""")
            .findAll(normalizedText)
            .map { it.value.trim() }
            .filter { token -> token.length >= 3 && token.lowercase() !in englishStopWords }
            .toList()
        val keywords = (detectedSkills + keywordTokens)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(12)

        val requirements = lines.filterIndexed { index, line ->
            val lowered = lowerLines[index]
            "require" in lowered || "자격" in lowered || "must" in lowered || line.startsWith("-")
        }.take(8)

        val niceToHave = lines.filterIndexed { index, _ ->
            val lowered = lowerLines[index]
            "preferred" in lowered || "우대" in lowered || "plus" in lowered || "nice to have" in lowered
        }.take(8)

        val responsibilities = lines.filterIndexed { index, line ->
            val lowered = lowerLines[index]
            "responsibil" in lowered || "what you'll do" in lowered || "업무" in lowered || "담당" in lowered ||
                line.startsWith("*")
        }.take(8)

        val inferredCompanyName = companyName
            ?: sourceUrl?.let(::inferCompanyFromUrl)
            ?: lines.firstOrNull { it.length <= 60 && !it.contains(":") }

        val inferredRoleName = roleName
            ?: lines.firstOrNull { roleHints.any { hint -> it.contains(hint, ignoreCase = true) } }

        val summary = buildString {
            append(inferredRoleName ?: "Job posting")
            if (!keywords.isNullOrEmpty()) {
                append(" focused on ")
                append(keywords.take(4).joinToString(", "))
            }
            if (responsibilities.isNotEmpty()) {
                append(". Main responsibilities include ")
                append(responsibilities.take(2).joinToString("; "))
            }
            append(".")
        }

        return ParsedJobPosting(
            companyName = inferredCompanyName,
            roleName = inferredRoleName,
            requirements = requirements,
            niceToHave = niceToHave,
            keywords = keywords,
            responsibilities = responsibilities,
            summary = summary,
        )
    }

    private fun inferCompanyFromUrl(sourceUrl: String): String? =
        runCatching {
            val host = URI(sourceUrl).host ?: return null
            host.removePrefix("www.").substringBefore('.').replace('-', ' ').replaceFirstChar { it.uppercase() }
        }.getOrNull()

    private fun decodeStringList(raw: String): List<String> =
        runCatching { objectMapper.readValue(raw, object : TypeReference<List<String>>() {}) }
            .getOrDefault(emptyList())

    private companion object {
        private val supportedInputTypes = setOf("text", "link")
        private val roleHints = listOf("engineer", "developer", "backend", "frontend", "platform", "data", "manager", "개발자", "엔지니어")
        private val englishStopWords = setOf("the", "and", "with", "for", "you", "your", "our", "will", "that", "this", "from", "have")
    }
}

private data class ParsedJobPosting(
    val companyName: String?,
    val roleName: String?,
    val requirements: List<String>,
    val niceToHave: List<String>,
    val keywords: List<String>,
    val responsibilities: List<String>,
    val summary: String,
)
