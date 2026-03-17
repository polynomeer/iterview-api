package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.interview.entity.InterviewRecordAnswerEntity
import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import com.example.interviewplatform.interview.entity.InterviewRecordQuestionEntity
import com.example.interviewplatform.interview.repository.InterviewRecordAnswerRepository
import com.example.interviewplatform.interview.repository.InterviewRecordFollowUpEdgeRepository
import com.example.interviewplatform.interview.repository.InterviewRecordQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewRecordRepository
import com.example.interviewplatform.resume.dto.CreateResumeQuestionHeatmapLinkRequest
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapItemDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapLinkDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapQuestionDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapSummaryDto
import com.example.interviewplatform.resume.dto.UpdateResumeQuestionHeatmapLinkRequest
import com.example.interviewplatform.resume.entity.ResumeQuestionHeatmapLinkEntity
import com.example.interviewplatform.resume.repository.ResumeCompetencyItemRepository
import com.example.interviewplatform.resume.repository.ResumeExperienceSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProfileSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectTagRepository
import com.example.interviewplatform.resume.repository.ResumeQuestionHeatmapLinkRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ResumeQuestionHeatmapService(
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeProfileSnapshotRepository: ResumeProfileSnapshotRepository,
    private val resumeCompetencyItemRepository: ResumeCompetencyItemRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeExperienceSnapshotRepository: ResumeExperienceSnapshotRepository,
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeProjectTagRepository: ResumeProjectTagRepository,
    private val interviewRecordRepository: InterviewRecordRepository,
    private val interviewRecordQuestionRepository: InterviewRecordQuestionRepository,
    private val interviewRecordAnswerRepository: InterviewRecordAnswerRepository,
    private val interviewRecordFollowUpEdgeRepository: InterviewRecordFollowUpEdgeRepository,
    private val resumeQuestionHeatmapLinkRepository: ResumeQuestionHeatmapLinkRepository,
    private val objectMapper: ObjectMapper,
    private val clockService: ClockService,
) {
    @Transactional(readOnly = true)
    fun getHeatmap(userId: Long, versionId: Long, scope: String): ResumeQuestionHeatmapDto {
        requireOwnedVersion(userId, versionId)
        val normalizedScope = scope.trim().lowercase()
        if (normalizedScope !in supportedScopes) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported heatmap scope: $scope")
        }
        val records = interviewRecordRepository.findByUserIdAndLinkedResumeVersionIdOrderByCreatedAtDesc(userId, versionId)
        if (records.isEmpty()) {
            return ResumeQuestionHeatmapDto(
                resumeVersionId = versionId,
                scope = normalizedScope,
                summary = ResumeQuestionHeatmapSummaryDto(
                    totalAnchors = 0,
                    totalLinkedQuestions = 0,
                    hottestAnchorLabel = null,
                    mostFollowedUpAnchorLabel = null,
                    weakestAnchorLabel = null,
                ),
                items = emptyList(),
            )
        }
        val questions = interviewRecordQuestionRepository.findByInterviewRecordIdInOrderByInterviewRecordIdAscOrderIndexAsc(records.map { it.id })
        val filteredQuestions = questions.filter { question ->
            when (normalizedScope) {
                "main" -> question.parentQuestionId == null
                "follow_up" -> question.parentQuestionId != null
                else -> true
            }
        }
        if (filteredQuestions.isEmpty()) {
            return ResumeQuestionHeatmapDto(
                resumeVersionId = versionId,
                scope = normalizedScope,
                summary = ResumeQuestionHeatmapSummaryDto(0, 0, null, null, null),
                items = emptyList(),
            )
        }

        val answers = interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(filteredQuestions.map { it.id })
            .associateBy { it.interviewRecordQuestionId }
        val followUpEdges = interviewRecordFollowUpEdgeRepository.findByInterviewRecordIdInOrderByInterviewRecordIdAscIdAsc(records.map { it.id })
        val outgoingFollowUpCount = followUpEdges.groupingBy { it.fromQuestionId }.eachCount()
        val manualLinks = resumeQuestionHeatmapLinkRepository.findByResumeVersionIdAndActiveTrue(versionId)
            .associateBy { it.interviewRecordQuestionId }
        val recordsById = records.associateBy { it.id }
        val projects = resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
        val anchorResolver = AnchorResolver(
            profile = resumeProfileSnapshotRepository.findByResumeVersionId(versionId),
            competencies = resumeCompetencyItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId),
            skills = resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(versionId),
            experiences = resumeExperienceSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId),
            projects = projects,
            projectTagsByProjectId = resumeProjectTagRepository
                .findByResumeProjectSnapshotIdInOrderByResumeProjectSnapshotIdAscDisplayOrderAscIdAsc(projects.map { it.id })
                .groupBy { it.resumeProjectSnapshotId },
        )

        val aggregated = linkedMapOf<AnchorIdentity, MutableHeatmapAccumulator>()
        filteredQuestions.forEach { question ->
            val record = recordsById[question.interviewRecordId] ?: return@forEach
            val resolution = resolveAnchor(question, manualLinks[question.id], anchorResolver) ?: return@forEach
            val anchor = resolution.anchor
            val answer = answers[question.id]
            val weaknessTags = decodeStringList(answer?.weaknessTagsJson)
            val pressure = isPressureQuestion(question)
            val followUpCount = outgoingFollowUpCount[question.id] ?: 0
            val questionDto = ResumeQuestionHeatmapQuestionDto(
                interviewRecordQuestionId = question.id,
                sourceInterviewRecordId = question.interviewRecordId,
                linkedQuestionId = question.linkedQuestionId,
                text = question.text,
                questionType = question.questionType,
                isFollowUp = question.parentQuestionId != null,
                followUpCount = followUpCount,
                pressureQuestion = pressure,
                weakAnswer = weaknessTags.isNotEmpty(),
                weaknessTags = weaknessTags,
                interviewDate = record.interviewDate,
                linkSource = resolution.linkSource,
                confidenceScore = resolution.confidenceScore,
            )
            val key = AnchorIdentity(anchor.anchorType, anchor.anchorRecordId, anchor.anchorKey)
            val accumulator = aggregated.getOrPut(key) {
                MutableHeatmapAccumulator(
                    anchorType = anchor.anchorType,
                    anchorRecordId = anchor.anchorRecordId,
                    anchorKey = anchor.anchorKey,
                    label = anchor.label,
                    snippet = anchor.snippet,
                )
            }
            accumulator.directQuestionCount += 1
            accumulator.followUpCount += followUpCount
            accumulator.pressureQuestionCount += if (pressure) 1 else 0
            accumulator.weaknessCount += if (weaknessTags.isNotEmpty()) 1 else 0
            accumulator.distinctInterviewIds += question.interviewRecordId
            accumulator.recentQuestionAt = maxOfNotNull(accumulator.recentQuestionAt, record.createdAt)
            accumulator.questions += questionDto
        }

        val items = aggregated.values.map { it.toDto() }.sortedByDescending { it.heatScore }
        val summary = ResumeQuestionHeatmapSummaryDto(
            totalAnchors = items.size,
            totalLinkedQuestions = items.sumOf { it.directQuestionCount },
            hottestAnchorLabel = items.maxByOrNull { it.heatScore }?.label,
            mostFollowedUpAnchorLabel = items.maxByOrNull { it.followUpCount }?.label,
            weakestAnchorLabel = items.maxByOrNull { it.weaknessCount }?.label,
        )
        return ResumeQuestionHeatmapDto(
            resumeVersionId = versionId,
            scope = normalizedScope,
            summary = summary,
            items = items,
        )
    }

    @Transactional
    fun createLink(
        userId: Long,
        versionId: Long,
        request: CreateResumeQuestionHeatmapLinkRequest,
    ): ResumeQuestionHeatmapLinkDto {
        requireOwnedVersion(userId, versionId)
        val question = requireLinkedQuestion(userId, versionId, request.interviewRecordQuestionId)
        validateAnchor(versionId, request.anchorType, request.anchorRecordId, request.anchorKey)
        val now = clockService.now()
        val existing = resumeQuestionHeatmapLinkRepository.findByInterviewRecordQuestionId(question.id)
        val saved = resumeQuestionHeatmapLinkRepository.save(
            ResumeQuestionHeatmapLinkEntity(
                id = existing?.id ?: 0,
                userId = userId,
                resumeVersionId = versionId,
                interviewRecordQuestionId = question.id,
                anchorType = request.anchorType.trim().lowercase(),
                anchorRecordId = request.anchorRecordId,
                anchorKey = request.anchorKey?.trim()?.takeIf { it.isNotEmpty() },
                linkSource = "manual",
                confidenceScore = request.confidenceScore,
                active = true,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return saved.toDto()
    }

    @Transactional
    fun updateLink(
        userId: Long,
        versionId: Long,
        linkId: Long,
        request: UpdateResumeQuestionHeatmapLinkRequest,
    ): ResumeQuestionHeatmapLinkDto {
        requireOwnedVersion(userId, versionId)
        val existing = resumeQuestionHeatmapLinkRepository.findByIdAndResumeVersionId(linkId, versionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume question heatmap link not found: $linkId")
        requireLinkedQuestion(userId, versionId, existing.interviewRecordQuestionId)
        val anchorType = request.anchorType?.trim()?.lowercase() ?: existing.anchorType
        val anchorRecordId = request.anchorRecordId ?: existing.anchorRecordId
        val anchorKey = request.anchorKey?.trim()?.takeIf { it.isNotEmpty() } ?: existing.anchorKey
        validateAnchor(versionId, anchorType, anchorRecordId, anchorKey)
        val updated = resumeQuestionHeatmapLinkRepository.save(
            ResumeQuestionHeatmapLinkEntity(
                id = existing.id,
                userId = existing.userId,
                resumeVersionId = existing.resumeVersionId,
                interviewRecordQuestionId = existing.interviewRecordQuestionId,
                anchorType = anchorType,
                anchorRecordId = anchorRecordId,
                anchorKey = anchorKey,
                linkSource = existing.linkSource,
                confidenceScore = request.confidenceScore ?: existing.confidenceScore,
                active = request.active ?: existing.active,
                createdAt = existing.createdAt,
                updatedAt = clockService.now(),
            ),
        )
        return updated.toDto()
    }

    private fun requireOwnedVersion(userId: Long, versionId: Long) =
        resumeVersionRepository.findById(versionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId") }
            .also { version ->
                val resumeOwnerId = resumeVersionRepository.findResumeOwnerIdByVersionId(version.id)
                if (resumeOwnerId != userId) {
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId")
                }
            }

    private fun requireLinkedQuestion(userId: Long, versionId: Long, interviewRecordQuestionId: Long): InterviewRecordQuestionEntity {
        val question = interviewRecordQuestionRepository.findById(interviewRecordQuestionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Interview record question not found: $interviewRecordQuestionId") }
        val record = interviewRecordRepository.findByIdAndUserId(question.interviewRecordId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Interview record question not found: $interviewRecordQuestionId")
        if (record.linkedResumeVersionId != versionId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview record question does not belong to resume version $versionId")
        }
        return question
    }

    private fun validateAnchor(versionId: Long, anchorType: String, anchorRecordId: Long?, anchorKey: String?) {
        when (anchorType.trim().lowercase()) {
            "project" -> if (anchorRecordId == null || resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId).none { it.id == anchorRecordId }) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid project anchorRecordId: $anchorRecordId")
            }
            "experience" -> if (anchorRecordId == null || resumeExperienceSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId).none { it.id == anchorRecordId }) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid experience anchorRecordId: $anchorRecordId")
            }
            "skill" -> if (anchorRecordId == null || resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(versionId).none { it.id == anchorRecordId }) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid skill anchorRecordId: $anchorRecordId")
            }
            "competency" -> if (anchorRecordId == null || resumeCompetencyItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId).none { it.id == anchorRecordId }) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid competency anchorRecordId: $anchorRecordId")
            }
            "profile", "summary" -> if (anchorKey.isNullOrBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "anchorKey is required for profile/summary anchors")
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported anchorType: $anchorType")
        }
    }

    private fun resolveAnchor(
        question: InterviewRecordQuestionEntity,
        manualLink: ResumeQuestionHeatmapLinkEntity?,
        resolver: AnchorResolver,
    ): ResolvedAnchorResolution? {
        manualLink?.takeIf { it.active }?.let {
            return resolver.resolve(it.anchorType, it.anchorRecordId, it.anchorKey)
                ?.let { anchor -> ResolvedAnchorResolution(anchor, it.linkSource, it.confidenceScore ?: manualConfidenceScore) }
        }
        if (question.derivedFromResumeRecordType != null && question.derivedFromResumeRecordId != null) {
            resolver.resolve(question.derivedFromResumeRecordType, question.derivedFromResumeRecordId, question.derivedFromResumeSection)?.let { anchor ->
                return ResolvedAnchorResolution(anchor, "inferred", inferredConfidenceScore)
            }
        }
        resolver.infer(question)?.let { anchor ->
            return ResolvedAnchorResolution(anchor, "heuristic", heuristicConfidenceScore)
        }
        return question.derivedFromResumeSection?.let { section ->
            resolver.resolve(section, null, section)?.let { anchor ->
                ResolvedAnchorResolution(anchor, "inferred", inferredConfidenceScore)
            }
        }
    }

    private fun isPressureQuestion(question: InterviewRecordQuestionEntity): Boolean {
        if (question.questionType.contains("pressure", ignoreCase = true)) {
            return true
        }
        val intentTags = decodeStringList(question.intentTagsJson).map { it.lowercase() }
        return intentTags.any { it in pressureIntentTags }
    }

    private fun decodeStringList(raw: String?): List<String> =
        runCatching { objectMapper.readValue(raw.orEmpty(), object : TypeReference<List<String>>() {}) }
            .getOrDefault(emptyList())

    private fun ResumeQuestionHeatmapLinkEntity.toDto(): ResumeQuestionHeatmapLinkDto = ResumeQuestionHeatmapLinkDto(
        id = id,
        resumeVersionId = resumeVersionId,
        interviewRecordQuestionId = interviewRecordQuestionId,
        anchorType = anchorType,
        anchorRecordId = anchorRecordId,
        anchorKey = anchorKey,
        linkSource = linkSource,
        confidenceScore = confidenceScore,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun maxOfNotNull(first: java.time.Instant?, second: java.time.Instant?): java.time.Instant? = when {
        first == null -> second
        second == null -> first
        first.isAfter(second) -> first
        else -> second
    }

    private companion object {
        private val supportedScopes = setOf("all", "main", "follow_up")
        private val pressureIntentTags = setOf("pressure", "challenge", "verification", "validation")
        private val manualConfidenceScore: BigDecimal = BigDecimal("1.0000")
        private val inferredConfidenceScore: BigDecimal = BigDecimal("0.9000")
        private val heuristicConfidenceScore: BigDecimal = BigDecimal("0.6500")
    }
}

private class AnchorResolver(
    private val profile: com.example.interviewplatform.resume.entity.ResumeProfileSnapshotEntity?,
    private val competencies: List<com.example.interviewplatform.resume.entity.ResumeCompetencyItemEntity>,
    private val skills: List<com.example.interviewplatform.resume.entity.ResumeSkillSnapshotEntity>,
    private val experiences: List<com.example.interviewplatform.resume.entity.ResumeExperienceSnapshotEntity>,
    private val projects: List<com.example.interviewplatform.resume.entity.ResumeProjectSnapshotEntity>,
    private val projectTagsByProjectId: Map<Long, List<com.example.interviewplatform.resume.entity.ResumeProjectTagEntity>>,
) {
    fun resolve(anchorType: String, anchorRecordId: Long?, anchorKey: String?): ResolvedAnchor? {
        val normalizedType = anchorType.lowercase()
        return when (normalizedType) {
            "project", "projects" -> projects.firstOrNull { it.id == anchorRecordId }?.let {
                ResolvedAnchor("project", it.id, null, it.title, it.contentText ?: it.summaryText)
            }
            "experience", "experiences" -> experiences.firstOrNull { it.id == anchorRecordId }?.let {
                val label = listOfNotNull(it.roleName, it.companyName).joinToString(" @ ").ifBlank { it.companyName ?: "Experience" }
                ResolvedAnchor("experience", it.id, null, label, it.summaryText)
            }
            "skill", "skills" -> skills.firstOrNull { it.id == anchorRecordId }?.let {
                ResolvedAnchor("skill", it.id, null, it.skillName, it.sourceText)
            }
            "competency", "competencies" -> competencies.firstOrNull { it.id == anchorRecordId }?.let {
                ResolvedAnchor("competency", it.id, null, it.title, it.description)
            }
            "summary", "profile" -> ResolvedAnchor("summary", null, anchorKey ?: "summary", profile?.headline ?: "Resume Summary", profile?.summaryText)
            else -> null
        }
    }

    fun infer(question: InterviewRecordQuestionEntity): ResolvedAnchor? {
        val questionText = "${question.text} ${question.normalizedText.orEmpty()}".lowercase()
        val projectMatch = projects.map { project ->
            project to scoreQuestionMatch(
                questionText,
                listOf(project.title, project.summaryText, project.contentText, project.techStackText) +
                    projectTagsByProjectId[project.id].orEmpty().flatMap { listOf(it.tagName, it.sourceText) },
            )
        }.maxByOrNull { it.second }
        if (projectMatch != null && projectMatch.second >= 2) {
            return ResolvedAnchor("project", projectMatch.first.id, null, projectMatch.first.title, projectMatch.first.contentText ?: projectMatch.first.summaryText)
        }
        val experienceMatch = experiences.map { experience ->
            experience to scoreQuestionMatch(questionText, listOf(experience.roleName, experience.companyName, experience.summaryText))
        }.maxByOrNull { it.second }
        if (experienceMatch != null && experienceMatch.second >= 2) {
            val experience = experienceMatch.first
            val label = listOfNotNull(experience.roleName, experience.companyName).joinToString(" @ ").ifBlank { experience.companyName ?: "Experience" }
            return ResolvedAnchor("experience", experience.id, null, label, experience.summaryText)
        }
        val skillMatch = skills.firstOrNull { questionText.contains(it.skillName.lowercase()) }
        if (skillMatch != null) {
            return ResolvedAnchor("skill", skillMatch.id, null, skillMatch.skillName, skillMatch.sourceText)
        }
        val competencyMatch = competencies.firstOrNull { questionText.contains(it.title.lowercase()) }
        if (competencyMatch != null) {
            return ResolvedAnchor("competency", competencyMatch.id, null, competencyMatch.title, competencyMatch.description)
        }
        return if (profile?.summaryText?.isNotBlank() == true) {
            ResolvedAnchor("summary", null, "summary", profile.headline ?: "Resume Summary", profile.summaryText)
        } else {
            null
        }
    }

    private fun scoreQuestionMatch(questionText: String, candidates: List<String?>): Int =
        candidates.filterNotNull().sumOf { candidate ->
            val lowered = candidate.lowercase()
            buildList {
                add(lowered)
                lowered.split(Regex("\\W+")).filter { it.length >= 3 }.forEach(::add)
            }.distinct().count { token -> token.isNotBlank() && questionText.contains(token) }
        }
}

private data class ResolvedAnchor(
    val anchorType: String,
    val anchorRecordId: Long?,
    val anchorKey: String?,
    val label: String,
    val snippet: String?,
)

private data class ResolvedAnchorResolution(
    val anchor: ResolvedAnchor,
    val linkSource: String,
    val confidenceScore: BigDecimal?,
)

private data class AnchorIdentity(
    val anchorType: String,
    val anchorRecordId: Long?,
    val anchorKey: String?,
)

private class MutableHeatmapAccumulator(
    val anchorType: String,
    val anchorRecordId: Long?,
    val anchorKey: String?,
    val label: String,
    val snippet: String?,
) {
    var directQuestionCount: Int = 0
    var followUpCount: Int = 0
    var pressureQuestionCount: Int = 0
    var weaknessCount: Int = 0
    val distinctInterviewIds: MutableSet<Long> = linkedSetOf()
    var recentQuestionAt: java.time.Instant? = null
    val questions: MutableList<ResumeQuestionHeatmapQuestionDto> = mutableListOf()

    fun toDto(): ResumeQuestionHeatmapItemDto {
        val heatScore = directQuestionCount.toDouble() +
            (followUpCount * 1.5) +
            (pressureQuestionCount * 2.0) +
            (distinctInterviewIds.size * 1.2) +
            (weaknessCount * 1.5)
        return ResumeQuestionHeatmapItemDto(
            anchorType = anchorType,
            anchorRecordId = anchorRecordId,
            anchorKey = anchorKey,
            label = label,
            snippet = snippet,
            heatScore = BigDecimal(heatScore).setScale(2, RoundingMode.HALF_UP).toDouble(),
            normalizedHeatLevel = when {
                heatScore >= 12.0 -> "critical"
                heatScore >= 7.0 -> "high"
                heatScore >= 3.0 -> "medium"
                else -> "low"
            },
            directQuestionCount = directQuestionCount,
            followUpCount = followUpCount,
            distinctInterviewCount = distinctInterviewIds.size,
            pressureQuestionCount = pressureQuestionCount,
            weaknessCount = weaknessCount,
            recentQuestionAt = recentQuestionAt,
            linkedQuestions = questions.sortedWith(
                compareByDescending<ResumeQuestionHeatmapQuestionDto> { it.followUpCount }
                    .thenByDescending { it.weakAnswer }
                    .thenByDescending { it.interviewRecordQuestionId },
            ),
        )
    }
}
