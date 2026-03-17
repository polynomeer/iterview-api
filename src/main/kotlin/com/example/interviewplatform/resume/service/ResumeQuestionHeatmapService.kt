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
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapAppliedFiltersDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapFilterSummaryDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapItemDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapLinkDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapOverlayTargetDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapOverlayTargetListDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapQuestionDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapSummaryDto
import com.example.interviewplatform.resume.dto.UpdateResumeQuestionHeatmapLinkRequest
import com.example.interviewplatform.resume.entity.ResumeDocumentOverlayTargetEntity
import com.example.interviewplatform.resume.entity.ResumeQuestionHeatmapLinkEntity
import com.example.interviewplatform.resume.repository.ResumeCompetencyItemRepository
import com.example.interviewplatform.resume.repository.ResumeDocumentOverlayTargetRepository
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
import java.time.LocalDate

@Service
class ResumeQuestionHeatmapService(
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeProfileSnapshotRepository: ResumeProfileSnapshotRepository,
    private val resumeCompetencyItemRepository: ResumeCompetencyItemRepository,
    private val resumeDocumentOverlayTargetRepository: ResumeDocumentOverlayTargetRepository,
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
    fun getHeatmap(
        userId: Long,
        versionId: Long,
        scope: String,
        weakOnly: Boolean = false,
        companyName: String? = null,
        interviewDateFrom: LocalDate? = null,
        interviewDateTo: LocalDate? = null,
        targetType: String? = null,
    ): ResumeQuestionHeatmapDto {
        val context = buildHeatmapContext(userId, versionId, scope, weakOnly, companyName, interviewDateFrom, interviewDateTo, targetType)
        if (context.items.isEmpty()) {
            return ResumeQuestionHeatmapDto(
                resumeVersionId = versionId,
                scope = context.scope,
                appliedFilters = context.appliedFilters,
                filterSummary = context.filterSummary,
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
        val summary = ResumeQuestionHeatmapSummaryDto(
            totalAnchors = context.items.size,
            totalLinkedQuestions = context.items.sumOf { it.directQuestionCount },
            hottestAnchorLabel = context.items.maxByOrNull { it.heatScore }?.label,
            mostFollowedUpAnchorLabel = context.items.maxByOrNull { it.followUpCount }?.label,
            weakestAnchorLabel = context.items.maxByOrNull { it.weaknessCount }?.label,
        )
        return ResumeQuestionHeatmapDto(
            resumeVersionId = versionId,
            scope = context.scope,
            appliedFilters = context.appliedFilters,
            filterSummary = context.filterSummary,
            summary = summary,
            items = context.items,
        )
    }

    @Transactional(readOnly = true)
    fun getOverlayTargets(
        userId: Long,
        versionId: Long,
        scope: String,
        weakOnly: Boolean = false,
        companyName: String? = null,
        interviewDateFrom: LocalDate? = null,
        interviewDateTo: LocalDate? = null,
        targetType: String? = null,
    ): ResumeQuestionHeatmapOverlayTargetListDto {
        val context = buildHeatmapContext(userId, versionId, scope, weakOnly, companyName, interviewDateFrom, interviewDateTo, targetType)
        return ResumeQuestionHeatmapOverlayTargetListDto(
            resumeVersionId = versionId,
            scope = context.scope,
            appliedFilters = context.appliedFilters,
            filterSummary = context.filterSummary,
            items = context.items.flatMap { it.overlayTargets }
                .sortedWith(
                    compareBy<ResumeQuestionHeatmapOverlayTargetDto> { it.anchorType }
                        .thenBy { it.anchorRecordId ?: Long.MAX_VALUE }
                        .thenBy { it.fieldPath }
                        .thenBy { it.sentenceIndex ?: -1 },
                ),
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
                overlayTargetType = request.overlayTargetType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                overlayFieldPath = request.overlayFieldPath?.trim()?.takeIf { it.isNotEmpty() },
                overlaySentenceIndex = request.overlaySentenceIndex,
                overlayTextSnippet = request.overlayTextSnippet?.trim()?.takeIf { it.isNotEmpty() },
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
        val overlayTargetType = request.overlayTargetType?.trim()?.lowercase() ?: existing.overlayTargetType
        val overlayFieldPath = request.overlayFieldPath?.trim()?.takeIf { it.isNotEmpty() } ?: existing.overlayFieldPath
        val overlaySentenceIndex = request.overlaySentenceIndex ?: existing.overlaySentenceIndex
        val overlayTextSnippet = request.overlayTextSnippet?.trim()?.takeIf { it.isNotEmpty() } ?: existing.overlayTextSnippet
        val updated = resumeQuestionHeatmapLinkRepository.save(
            ResumeQuestionHeatmapLinkEntity(
                id = existing.id,
                userId = existing.userId,
                resumeVersionId = existing.resumeVersionId,
                interviewRecordQuestionId = existing.interviewRecordQuestionId,
                anchorType = anchorType,
                anchorRecordId = anchorRecordId,
                anchorKey = anchorKey,
                overlayTargetType = overlayTargetType,
                overlayFieldPath = overlayFieldPath,
                overlaySentenceIndex = overlaySentenceIndex,
                overlayTextSnippet = overlayTextSnippet,
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

    private fun buildHeatmapContext(
        userId: Long,
        versionId: Long,
        scope: String,
        weakOnly: Boolean,
        companyName: String?,
        interviewDateFrom: LocalDate?,
        interviewDateTo: LocalDate?,
        targetType: String?,
    ): HeatmapContext {
        requireOwnedVersion(userId, versionId)
        val normalizedScope = scope.trim().lowercase()
        if (normalizedScope !in supportedScopes) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported heatmap scope: $scope")
        }
        val normalizedTargetType = targetType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (normalizedTargetType != null && normalizedTargetType !in supportedTargetTypes) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported heatmap targetType: $targetType")
        }
        val normalizedCompanyName = companyName?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        val records = interviewRecordRepository.findByUserIdAndLinkedResumeVersionIdOrderByCreatedAtDesc(userId, versionId)
            .filter { record ->
                val companyMatches = normalizedCompanyName == null ||
                    record.companyName?.lowercase()?.contains(normalizedCompanyName) == true
                val fromMatches = interviewDateFrom == null || (record.interviewDate != null && !record.interviewDate.isBefore(interviewDateFrom))
                val toMatches = interviewDateTo == null || (record.interviewDate != null && !record.interviewDate.isAfter(interviewDateTo))
                companyMatches && fromMatches && toMatches
            }
        if (records.isEmpty()) {
            return HeatmapContext(
                scope = normalizedScope,
                appliedFilters = ResumeQuestionHeatmapAppliedFiltersDto(
                    scope = normalizedScope,
                    weakOnly = weakOnly,
                    companyName = companyName?.trim()?.takeIf { it.isNotEmpty() },
                    interviewDateFrom = interviewDateFrom,
                    interviewDateTo = interviewDateTo,
                    targetType = normalizedTargetType,
                ),
                filterSummary = ResumeQuestionHeatmapFilterSummaryDto(
                    totalQuestions = 0,
                    weakQuestionCount = 0,
                    pressureQuestionCount = 0,
                    followUpQuestionCount = 0,
                    distinctInterviewCount = 0,
                    distinctCompanyCount = 0,
                    companyNames = emptyList(),
                    availableTargetTypes = emptyList(),
                    targetTypeCounts = emptyMap(),
                    earliestInterviewDate = null,
                    latestInterviewDate = null,
                ),
                items = emptyList(),
            )
        }

        val allQuestions = interviewRecordQuestionRepository.findByInterviewRecordIdInOrderByInterviewRecordIdAscOrderIndexAsc(records.map { it.id })
        val answersByQuestionId = interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(allQuestions.map { it.id })
            .associateBy { it.interviewRecordQuestionId }
        val filteredQuestions = allQuestions.filter { question ->
            val scopeMatches = when (normalizedScope) {
                "main" -> question.parentQuestionId == null
                "follow_up" -> question.parentQuestionId != null
                else -> true
            }
            val answer = answersByQuestionId[question.id]
            val weakMatches = !weakOnly || decodeStringList(answer?.weaknessTagsJson).isNotEmpty()
            scopeMatches && weakMatches
        }
        if (filteredQuestions.isEmpty()) {
            return HeatmapContext(
                scope = normalizedScope,
                appliedFilters = ResumeQuestionHeatmapAppliedFiltersDto(
                    scope = normalizedScope,
                    weakOnly = weakOnly,
                    companyName = companyName?.trim()?.takeIf { it.isNotEmpty() },
                    interviewDateFrom = interviewDateFrom,
                    interviewDateTo = interviewDateTo,
                    targetType = normalizedTargetType,
                ),
                filterSummary = ResumeQuestionHeatmapFilterSummaryDto(
                    totalQuestions = 0,
                    weakQuestionCount = 0,
                    pressureQuestionCount = 0,
                    followUpQuestionCount = 0,
                    distinctInterviewCount = 0,
                    distinctCompanyCount = 0,
                    companyNames = emptyList(),
                    availableTargetTypes = emptyList(),
                    targetTypeCounts = emptyMap(),
                    earliestInterviewDate = null,
                    latestInterviewDate = null,
                ),
                items = emptyList(),
            )
        }

        val answers = answersByQuestionId.filterKeys { questionId -> filteredQuestions.any { it.id == questionId } }
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
        val overlayTargetsByAnchor = resumeDocumentOverlayTargetRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
            .groupBy { AnchorIdentity(it.anchorType, it.anchorRecordId, it.anchorKey) }

        val aggregated = linkedMapOf<AnchorIdentity, MutableHeatmapAccumulator>()
        val overlaySelectionByQuestionId = mutableMapOf<Long, OverlayTargetKey>()
        val targetTypeCounts = linkedMapOf<String, Int>()
        val includedQuestionIds = linkedSetOf<Long>()
        val includedInterviewIds = linkedSetOf<Long>()
        val includedCompanyNames = linkedSetOf<String>()
        var weakQuestionCount = 0
        var pressureQuestionCount = 0
        var followUpQuestionCount = 0
        var earliestInterviewDate: LocalDate? = null
        var latestInterviewDate: LocalDate? = null

        filteredQuestions.forEach { question ->
            val record = recordsById[question.interviewRecordId] ?: return@forEach
            val manualLink = manualLinks[question.id]
            val resolution = resolveAnchor(question, manualLink, anchorResolver) ?: return@forEach
            val anchor = resolution.anchor
            val key = AnchorIdentity(anchor.anchorType, anchor.anchorRecordId, anchor.anchorKey)
            val accumulator = aggregated.getOrPut(key) {
                MutableHeatmapAccumulator(
                    anchorType = anchor.anchorType,
                    anchorRecordId = anchor.anchorRecordId,
                    anchorKey = anchor.anchorKey,
                    label = anchor.label,
                    snippet = anchor.snippet,
                    overlayTargets = buildOverlayAccumulators(anchor, overlayTargetsByAnchor[key].orEmpty()),
                )
            }
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
            val overlayTargetKey = selectOverlayTarget(
                question = question,
                accumulator = accumulator,
                manualLink = manualLink,
                parentOverlayTargetKey = question.parentQuestionId?.let(overlaySelectionByQuestionId::get),
            )
            if (normalizedTargetType != null && overlayTargetKey.targetType != normalizedTargetType) {
                return@forEach
            }
            overlaySelectionByQuestionId[question.id] = overlayTargetKey

            accumulator.directQuestionCount += 1
            accumulator.followUpCount += followUpCount
            accumulator.pressureQuestionCount += if (pressure) 1 else 0
            accumulator.weaknessCount += if (weaknessTags.isNotEmpty()) 1 else 0
            accumulator.distinctInterviewIds += question.interviewRecordId
            accumulator.recentQuestionAt = maxOfNotNull(accumulator.recentQuestionAt, record.createdAt)
            accumulator.questions += questionDto
            accumulator.overlayTargets.getValue(overlayTargetKey).apply {
                questionCount += 1
                this.followUpCount += followUpCount
                pressureQuestionCount += if (pressure) 1 else 0
                weaknessCount += if (weaknessTags.isNotEmpty()) 1 else 0
                questions += questionDto
            }
            targetTypeCounts[overlayTargetKey.targetType] = (targetTypeCounts[overlayTargetKey.targetType] ?: 0) + 1
            includedQuestionIds += question.id
            includedInterviewIds += question.interviewRecordId
            record.companyName?.trim()?.takeIf { it.isNotEmpty() }?.let(includedCompanyNames::add)
            if (weaknessTags.isNotEmpty()) {
                weakQuestionCount += 1
            }
            if (pressure) {
                pressureQuestionCount += 1
            }
            if (question.parentQuestionId != null) {
                followUpQuestionCount += 1
            }
            record.interviewDate?.let { interviewDate ->
                earliestInterviewDate = minOfNotNull(earliestInterviewDate, interviewDate)
                latestInterviewDate = maxOfNotNull(latestInterviewDate, interviewDate)
            }
        }

        val visibleItems = aggregated.values
            .map { it.toDto() }
            .filter { it.directQuestionCount > 0 }
            .map { item ->
                if (normalizedTargetType == null) {
                    item
                } else {
                    item.copy(
                        overlayTargets = item.overlayTargets.filter { overlayTarget ->
                            overlayTarget.targetType == normalizedTargetType && overlayTarget.questionCount > 0
                        },
                    )
                }
            }
            .sortedByDescending { it.heatScore }

        return HeatmapContext(
            scope = normalizedScope,
            appliedFilters = ResumeQuestionHeatmapAppliedFiltersDto(
                scope = normalizedScope,
                weakOnly = weakOnly,
                companyName = companyName?.trim()?.takeIf { it.isNotEmpty() },
                interviewDateFrom = interviewDateFrom,
                interviewDateTo = interviewDateTo,
                targetType = normalizedTargetType,
            ),
            filterSummary = ResumeQuestionHeatmapFilterSummaryDto(
                totalQuestions = includedQuestionIds.size,
                weakQuestionCount = weakQuestionCount,
                pressureQuestionCount = pressureQuestionCount,
                followUpQuestionCount = followUpQuestionCount,
                distinctInterviewCount = includedInterviewIds.size,
                distinctCompanyCount = includedCompanyNames.size,
                companyNames = includedCompanyNames.toList().sorted(),
                availableTargetTypes = targetTypeCounts.keys.sorted(),
                targetTypeCounts = targetTypeCounts.toSortedMap(),
                earliestInterviewDate = earliestInterviewDate,
                latestInterviewDate = latestInterviewDate,
            ),
            items = visibleItems,
        )
    }

    private fun buildOverlayAccumulators(
        anchor: ResolvedAnchor,
        persistedTargets: List<ResumeDocumentOverlayTargetEntity>,
    ): LinkedHashMap<OverlayTargetKey, MutableOverlayTargetAccumulator> {
        val targets = linkedMapOf<OverlayTargetKey, MutableOverlayTargetAccumulator>()
        val anchorBlockTargets = persistedTargets.filter { it.targetType == "block" && it.fieldPath == wholeAnchorFieldPath }
        val normalizedTargets = if (anchorBlockTargets.isNotEmpty()) {
            persistedTargets
        } else {
            listOf(
                ResumeDocumentOverlayTargetEntity(
                    id = 0,
                    resumeVersionId = 0,
                    anchorType = anchor.anchorType,
                    anchorRecordId = anchor.anchorRecordId,
                    anchorKey = anchor.anchorKey,
                    targetType = "block",
                    fieldPath = wholeAnchorFieldPath,
                    textSnippet = anchor.snippet ?: anchor.label,
                    textStartOffset = null,
                    textEndOffset = null,
                    sentenceIndex = null,
                    paragraphIndex = null,
                    displayOrder = Int.MIN_VALUE,
                    createdAt = clockService.now(),
                    updatedAt = clockService.now(),
                ),
            ) + persistedTargets
        }
        normalizedTargets.sortedWith(compareBy<ResumeDocumentOverlayTargetEntity> { it.displayOrder }.thenBy { it.id }).forEach { target ->
            val key = OverlayTargetKey(target.id.takeIf { it != 0L }, target.targetType, target.fieldPath, target.sentenceIndex)
            targets[key] = MutableOverlayTargetAccumulator(target)
        }
        return targets
    }

    private fun selectOverlayTarget(
        question: InterviewRecordQuestionEntity,
        accumulator: MutableHeatmapAccumulator,
        manualLink: ResumeQuestionHeatmapLinkEntity?,
        parentOverlayTargetKey: OverlayTargetKey?,
    ): OverlayTargetKey {
        resolveManualOverlayTargetKey(manualLink, accumulator)?.let { return it }
        val keywordTargets = accumulator.overlayTargets.filterValues { it.targetType == "keyword" }
        val bestKeywordMatch = keywordTargets.maxByOrNull { (_, target) -> scoreOverlayTargetMatch(question, target.textSnippet) }
        val bestKeywordScore = bestKeywordMatch?.let { scoreOverlayTargetMatch(question, it.value.textSnippet) } ?: 0
        if (bestKeywordScore >= keywordMatchThreshold) {
            return bestKeywordMatch!!.key
        }
        val phraseTargets = accumulator.overlayTargets.filterValues { it.targetType == "phrase" }
        val bestPhraseMatch = phraseTargets.maxByOrNull { (_, target) -> scoreOverlayTargetMatch(question, target.textSnippet) }
        val bestPhraseScore = bestPhraseMatch?.let { scoreOverlayTargetMatch(question, it.value.textSnippet) } ?: 0
        if (bestPhraseScore >= phraseMatchThreshold) {
            return bestPhraseMatch!!.key
        }
        val sentenceTargets = accumulator.overlayTargets.filterValues { it.targetType == "sentence" }
        val bestSentenceMatch = sentenceTargets.maxByOrNull { (_, target) -> scoreOverlayTargetMatch(question, target.textSnippet) }
        val bestSentenceScore = bestSentenceMatch?.let { scoreOverlayTargetMatch(question, it.value.textSnippet) } ?: 0
        val bestSentenceTarget = bestSentenceMatch?.value
        val parentOverlayTarget = parentOverlayTargetKey?.let(accumulator.overlayTargets::get)
        val parentSentenceScore = parentOverlayTargetKey
            ?.takeIf { it.targetType == "sentence" }
            ?.let { parentOverlayTarget }
            ?.let { scoreOverlayTargetMatch(question, it.textSnippet) }
            ?: 0
        if (question.parentQuestionId != null && parentOverlayTargetKey != null && parentOverlayTargetKey.targetType == "sentence" && parentOverlayTarget != null) {
            if (bestSentenceTarget != null && bestSentenceTarget.textSnippet.length >= genericSentenceSnippetThreshold) {
                return parentOverlayTargetKey
            }
            if (parentSentenceScore >= parentSentenceMatchThreshold && parentSentenceScore + parentSentenceBias >= bestSentenceScore) {
                return parentOverlayTargetKey
            }
        }
        if (bestSentenceScore >= sentenceMatchThreshold) {
            return bestSentenceMatch!!.key
        }
        if (question.parentQuestionId != null && parentOverlayTargetKey != null && parentOverlayTargetKey.targetType == "sentence" && parentOverlayTarget != null) {
            return parentOverlayTargetKey
        }
        return accumulator.overlayTargets.entries
            .firstOrNull { it.value.targetType == "block" && it.value.fieldPath == wholeAnchorFieldPath }
            ?.key
            ?: accumulator.overlayTargets.entries.first().key
    }

    private fun resolveManualOverlayTargetKey(
        manualLink: ResumeQuestionHeatmapLinkEntity?,
        accumulator: MutableHeatmapAccumulator,
    ): OverlayTargetKey? {
        val overlayTargetType = manualLink?.overlayTargetType ?: return null
        val matchingTargets = accumulator.overlayTargets.entries.filter { (_, target) ->
            target.targetType == overlayTargetType &&
                target.fieldPath == manualLink.overlayFieldPath &&
                target.sentenceIndex == manualLink.overlaySentenceIndex
        }
        if (matchingTargets.isEmpty()) {
            return null
        }
        val normalizedSnippet = manualLink.overlayTextSnippet
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::normalizeOverlayMatchText)
        if (normalizedSnippet == null) {
            return matchingTargets.first().key
        }
        return matchingTargets.firstOrNull { (_, target) ->
            val normalizedTargetSnippet = normalizeOverlayMatchText(target.textSnippet)
            normalizedTargetSnippet == normalizedSnippet ||
                normalizedTargetSnippet.contains(normalizedSnippet) ||
                normalizedSnippet.contains(normalizedTargetSnippet)
        }?.key ?: matchingTargets.first().key
    }

    private fun scoreOverlayTargetMatch(question: InterviewRecordQuestionEntity, targetText: String): Int {
        val questionText = buildString {
            append(question.text)
            append(' ')
            append(question.normalizedText.orEmpty())
            decodeStringList(question.topicTagsJson).forEach {
                append(' ')
                append(it)
            }
        }
        val normalizedQuestionText = normalizeOverlayMatchText(questionText)
        val normalizedTargetText = normalizeOverlayMatchText(targetText)
        val tokenScore = buildList {
            add(normalizedTargetText)
            normalizedTargetText.split(' ').filter { it.length >= 2 }.forEach(::add)
        }.distinct().count { token -> token.isNotBlank() && normalizedQuestionText.contains(token) }
        val phraseScore = buildOverlayPhraseCandidates(questionText)
            .count { phrase -> phrase.length >= 6 && normalizedTargetText.contains(phrase) }
        val exactishBonus = when {
            normalizedQuestionText.isNotBlank() && normalizedTargetText.contains(normalizedQuestionText) -> 6
            normalizedTargetText.isNotBlank() && normalizedQuestionText.contains(normalizedTargetText) -> 4
            else -> 0
        }
        return tokenScore + phraseScore + exactishBonus
    }

    private fun normalizeOverlayMatchText(value: String): String =
        value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun buildOverlayPhraseCandidates(value: String): List<String> {
        val words = normalizeOverlayMatchText(value)
            .split(' ')
            .filter { it.length >= 2 }
        if (words.size < 2) {
            return emptyList()
        }
        val candidates = mutableListOf<String>()
        for (size in minOf(5, words.size) downTo 2) {
            for (index in 0..words.size - size) {
                candidates += words.subList(index, index + size).joinToString(" ")
            }
        }
        return candidates.distinct()
    }

    private fun ResumeQuestionHeatmapLinkEntity.toDto(): ResumeQuestionHeatmapLinkDto = ResumeQuestionHeatmapLinkDto(
        id = id,
        resumeVersionId = resumeVersionId,
        interviewRecordQuestionId = interviewRecordQuestionId,
        anchorType = anchorType,
        anchorRecordId = anchorRecordId,
        anchorKey = anchorKey,
        overlayTargetType = overlayTargetType,
        overlayFieldPath = overlayFieldPath,
        overlaySentenceIndex = overlaySentenceIndex,
        overlayTextSnippet = overlayTextSnippet,
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

    private fun minOfNotNull(first: LocalDate?, second: LocalDate?): LocalDate? = when {
        first == null -> second
        second == null -> first
        first.isBefore(second) -> first
        else -> second
    }

    private fun maxOfNotNull(first: LocalDate?, second: LocalDate?): LocalDate? = when {
        first == null -> second
        second == null -> first
        first.isAfter(second) -> first
        else -> second
    }

    private companion object {
        private val supportedScopes = setOf("all", "main", "follow_up")
        private val supportedTargetTypes = setOf("block", "sentence", "phrase", "keyword")
        private val pressureIntentTags = setOf("pressure", "challenge", "verification", "validation")
        private val manualConfidenceScore: BigDecimal = BigDecimal("1.0000")
        private val inferredConfidenceScore: BigDecimal = BigDecimal("0.9000")
        private val heuristicConfidenceScore: BigDecimal = BigDecimal("0.6500")
        private const val wholeAnchorFieldPath = "anchor.block"
        private const val keywordMatchThreshold = 2
        private const val phraseMatchThreshold = 3
        private const val sentenceMatchThreshold = 2
        private const val parentSentenceMatchThreshold = 1
        private const val parentSentenceBias = 1
        private const val genericSentenceSnippetThreshold = 80
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
    val overlayTargets: LinkedHashMap<OverlayTargetKey, MutableOverlayTargetAccumulator>,
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
            overlayTargets = overlayTargets.values.map { it.toDto() },
            linkedQuestions = questions.sortedWith(
                compareByDescending<ResumeQuestionHeatmapQuestionDto> { it.followUpCount }
                    .thenByDescending { it.weakAnswer }
                    .thenByDescending { it.interviewRecordQuestionId },
            ),
        )
    }
}

private class MutableOverlayTargetAccumulator(
    target: ResumeDocumentOverlayTargetEntity,
) {
    val id: Long? = target.id.takeIf { it != 0L }
    val anchorType: String = target.anchorType
    val anchorRecordId: Long? = target.anchorRecordId
    val anchorKey: String? = target.anchorKey
    val targetType: String = target.targetType
    val fieldPath: String = target.fieldPath
    val textSnippet: String = target.textSnippet
    val textStartOffset: Int? = target.textStartOffset
    val textEndOffset: Int? = target.textEndOffset
    val sentenceIndex: Int? = target.sentenceIndex
    val paragraphIndex: Int? = target.paragraphIndex
    var questionCount: Int = 0
    var followUpCount: Int = 0
    var pressureQuestionCount: Int = 0
    var weaknessCount: Int = 0
    val questions: MutableList<ResumeQuestionHeatmapQuestionDto> = mutableListOf()

    fun toDto(): ResumeQuestionHeatmapOverlayTargetDto {
        val heatScore = questionCount.toDouble() +
            (followUpCount * 1.5) +
            (pressureQuestionCount * 2.0) +
            (weaknessCount * 1.5)
        return ResumeQuestionHeatmapOverlayTargetDto(
            id = id,
            anchorType = anchorType,
            anchorRecordId = anchorRecordId,
            anchorKey = anchorKey,
            targetType = targetType,
            targetKey = buildTargetKey(targetType, fieldPath, sentenceIndex, textSnippet),
            fieldPath = fieldPath,
            textSnippet = textSnippet,
            textStartOffset = textStartOffset,
            textEndOffset = textEndOffset,
            sentenceIndex = sentenceIndex,
            paragraphIndex = paragraphIndex,
            heatScore = BigDecimal(heatScore).setScale(2, RoundingMode.HALF_UP).toDouble(),
            normalizedHeatLevel = when {
                heatScore >= 8.0 -> "critical"
                heatScore >= 5.0 -> "high"
                heatScore >= 2.0 -> "medium"
                else -> "low"
            },
            questionCount = questionCount,
            followUpCount = followUpCount,
            pressureQuestionCount = pressureQuestionCount,
            weaknessCount = weaknessCount,
            linkedQuestions = questions.sortedWith(
                compareByDescending<ResumeQuestionHeatmapQuestionDto> { it.followUpCount }
                    .thenByDescending { it.weakAnswer }
                    .thenByDescending { it.interviewRecordQuestionId },
            ),
        )
    }
}

private fun buildTargetKey(targetType: String, fieldPath: String, sentenceIndex: Int?, textSnippet: String): String =
    listOf(targetType, fieldPath, sentenceIndex?.toString() ?: "root", textSnippet.take(48))
        .joinToString(":")

private data class OverlayTargetKey(
    val id: Long?,
    val targetType: String,
    val fieldPath: String,
    val sentenceIndex: Int?,
)

private data class HeatmapContext(
    val scope: String,
    val appliedFilters: ResumeQuestionHeatmapAppliedFiltersDto,
    val filterSummary: ResumeQuestionHeatmapFilterSummaryDto,
    val items: List<ResumeQuestionHeatmapItemDto>,
)
