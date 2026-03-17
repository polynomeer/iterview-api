package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.jobposting.repository.JobPostingRepository
import com.example.interviewplatform.resume.dto.CreateResumeAnalysisRequest
import com.example.interviewplatform.resume.dto.ResumeAnalysisDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisListItemDto
import com.example.interviewplatform.resume.dto.UpdateResumeAnalysisSuggestionRequest
import com.example.interviewplatform.resume.entity.ResumeAnalysisEntity
import com.example.interviewplatform.resume.entity.ResumeAnalysisSuggestionEntity
import com.example.interviewplatform.resume.mapper.ResumeAnalysisMapper
import com.example.interviewplatform.resume.repository.ResumeAnalysisRepository
import com.example.interviewplatform.resume.repository.ResumeAnalysisSuggestionRepository
import com.example.interviewplatform.resume.repository.ResumeCompetencyItemRepository
import com.example.interviewplatform.resume.repository.ResumeExperienceSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProfileSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectTagRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ResumeAnalysisService(
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeProfileSnapshotRepository: ResumeProfileSnapshotRepository,
    private val resumeCompetencyItemRepository: ResumeCompetencyItemRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeExperienceSnapshotRepository: ResumeExperienceSnapshotRepository,
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeProjectTagRepository: ResumeProjectTagRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val jobPostingRepository: JobPostingRepository,
    private val resumeAnalysisRepository: ResumeAnalysisRepository,
    private val resumeAnalysisSuggestionRepository: ResumeAnalysisSuggestionRepository,
    private val objectMapper: ObjectMapper,
    private val clockService: ClockService,
) {
    @Transactional(readOnly = true)
    fun listAnalyses(userId: Long, versionId: Long): List<ResumeAnalysisListItemDto> {
        requireOwnedVersion(userId, versionId)
        return resumeAnalysisRepository.findByResumeVersionIdOrderByCreatedAtDesc(versionId).map(ResumeAnalysisMapper::toListItemDto)
    }

    @Transactional(readOnly = true)
    fun getAnalysis(userId: Long, versionId: Long, analysisId: Long): ResumeAnalysisDto {
        requireOwnedVersion(userId, versionId)
        val analysis = requireOwnedAnalysis(userId, analysisId)
        if (analysis.resumeVersionId != versionId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis not found: $analysisId")
        }
        return toDetailDto(analysis)
    }

    @Transactional
    fun createAnalysis(userId: Long, versionId: Long, request: CreateResumeAnalysisRequest): ResumeAnalysisDto {
        val version = requireOwnedVersion(userId, versionId)
        val jobPosting = request.jobPostingId?.let { jobPostingId ->
            jobPostingRepository.findByIdAndUserId(jobPostingId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found: $jobPostingId")
        }
        val resumeSnapshot = buildResumeSnapshot(version.id)
        val jobPostingSnapshot = jobPosting?.let { posting ->
            JobPostingSnapshot(
                keywords = decodeStringList(posting.parsedKeywordsJson),
                requirements = decodeStringList(posting.parsedRequirementsJson),
                responsibilities = decodeStringList(posting.parsedResponsibilitiesJson),
                companyName = posting.companyName,
                roleName = posting.roleName,
            )
        } ?: JobPostingSnapshot(
            keywords = emptyList(),
            requirements = emptyList(),
            responsibilities = emptyList(),
            companyName = null,
            roleName = null,
        )

        val generated = generateAnalysis(resumeSnapshot, jobPostingSnapshot, request.preferredFormatType)
        val now = clockService.now()
        val savedAnalysis = resumeAnalysisRepository.save(
            ResumeAnalysisEntity(
                userId = userId,
                resumeVersionId = version.id,
                jobPostingId = jobPosting?.id,
                status = STATUS_COMPLETED,
                overallScore = generated.overallScore,
                matchSummary = generated.matchSummary,
                strongMatchesJson = objectMapper.writeValueAsString(generated.strongMatches),
                missingKeywordsJson = objectMapper.writeValueAsString(generated.missingKeywords),
                weakSignalsJson = objectMapper.writeValueAsString(generated.weakSignals),
                recommendedFocusAreasJson = objectMapper.writeValueAsString(generated.recommendedFocusAreas),
                suggestedHeadline = generated.suggestedHeadline,
                suggestedSummary = generated.suggestedSummary,
                recommendedFormatType = generated.recommendedFormatType,
                createdAt = now,
                updatedAt = now,
            ),
        )
        resumeAnalysisSuggestionRepository.saveAll(
            generated.suggestions.mapIndexed { index, suggestion ->
                ResumeAnalysisSuggestionEntity(
                    resumeAnalysisId = savedAnalysis.id,
                    sectionKey = suggestion.sectionKey,
                    originalText = suggestion.originalText,
                    suggestedText = suggestion.suggestedText,
                    reason = suggestion.reason,
                    suggestionType = suggestion.suggestionType,
                    accepted = false,
                    displayOrder = index,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        return toDetailDto(savedAnalysis)
    }

    @Transactional
    fun updateSuggestion(
        userId: Long,
        versionId: Long,
        analysisId: Long,
        suggestionId: Long,
        request: UpdateResumeAnalysisSuggestionRequest,
    ): ResumeAnalysisDto {
        requireOwnedVersion(userId, versionId)
        val analysis = requireOwnedAnalysis(userId, analysisId)
        if (analysis.resumeVersionId != versionId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis not found: $analysisId")
        }
        val suggestion = resumeAnalysisSuggestionRepository.findByIdAndResumeAnalysisId(suggestionId, analysis.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis suggestion not found: $suggestionId")
        val now = clockService.now()
        resumeAnalysisSuggestionRepository.save(
            ResumeAnalysisSuggestionEntity(
                id = suggestion.id,
                resumeAnalysisId = suggestion.resumeAnalysisId,
                sectionKey = suggestion.sectionKey,
                originalText = suggestion.originalText,
                suggestedText = suggestion.suggestedText,
                reason = suggestion.reason,
                suggestionType = suggestion.suggestionType,
                accepted = request.accepted,
                displayOrder = suggestion.displayOrder,
                createdAt = suggestion.createdAt,
                updatedAt = now,
            ),
        )
        return toDetailDto(analysis)
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

    private fun requireOwnedAnalysis(userId: Long, analysisId: Long): ResumeAnalysisEntity =
        resumeAnalysisRepository.findByIdAndUserId(analysisId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis not found: $analysisId")

    private fun toDetailDto(entity: ResumeAnalysisEntity): ResumeAnalysisDto =
        ResumeAnalysisMapper.toDetailDto(
            entity = entity,
            strongMatches = decodeStringList(entity.strongMatchesJson),
            missingKeywords = decodeStringList(entity.missingKeywordsJson),
            weakSignals = decodeStringList(entity.weakSignalsJson),
            recommendedFocusAreas = decodeStringList(entity.recommendedFocusAreasJson),
            suggestions = resumeAnalysisSuggestionRepository.findByResumeAnalysisIdOrderByDisplayOrderAscIdAsc(entity.id)
                .map(ResumeAnalysisMapper::toSuggestionDto),
        )

    private fun buildResumeSnapshot(versionId: Long): ResumeSnapshot {
        val profile = resumeProfileSnapshotRepository.findByResumeVersionId(versionId)
        val competencies = resumeCompetencyItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
        val skills = resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(versionId)
        val experiences = resumeExperienceSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
        val projects = resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
        val projectTags = resumeProjectTagRepository.findByResumeVersionIdOrderByProjectDisplayOrderAscTagDisplayOrderAsc(versionId)
        val risks = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(versionId)

        val projectTagNames = projectTags.map { it.tagName }
        val resumeKeywords = (
            skills.map { it.skillName } +
                competencies.flatMap { listOf(it.title, it.description) } +
                experiences.mapNotNull { it.companyName } +
                experiences.mapNotNull { it.roleName } +
                projects.map { it.title } +
                projectTagNames
            ).map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        return ResumeSnapshot(
            headlineSource = profile?.headline ?: profile?.fullName ?: experiences.firstOrNull()?.roleName,
            summarySource = profile?.summaryText ?: competencies.firstOrNull()?.description ?: experiences.firstOrNull()?.summaryText,
            resumeKeywords = resumeKeywords,
            quantifiedEvidenceCount = (experiences.mapNotNull { it.summaryText } + projects.mapNotNull { it.contentText })
                .count { it.contains(Regex("\\d")) },
            riskSignals = risks.map { it.riskType.replace('_', ' ').lowercase() },
            topProjectTitles = projects.map { it.title }.take(3),
            topExperienceTitles = experiences.mapNotNull { it.roleName ?: it.companyName }.take(3),
        )
    }

    private fun generateAnalysis(
        resume: ResumeSnapshot,
        posting: JobPostingSnapshot,
        preferredFormatType: String?,
    ): GeneratedResumeAnalysis {
        val postingKeywords = (posting.keywords + posting.requirements.flatMap(::extractInlineKeywords)).distinct()
        val matchedKeywords = postingKeywords.filter { keyword ->
            resume.resumeKeywords.any { it.equals(keyword, ignoreCase = true) }
        }.distinct()
        val missingKeywords = postingKeywords.filterNot { keyword ->
            resume.resumeKeywords.any { it.equals(keyword, ignoreCase = true) }
        }.distinct().take(8)

        val weakSignals = buildList {
            if (resume.quantifiedEvidenceCount == 0) add("성과를 수치로 드러내는 표현이 부족합니다.")
            if (resume.riskSignals.isNotEmpty()) add("방어가 필요한 이력서 리스크가 ${resume.riskSignals.size}건 있습니다.")
            if (matchedKeywords.isEmpty() && postingKeywords.isNotEmpty()) add("공고 핵심 키워드와 직접 일치하는 경험이 약하게 드러납니다.")
        }

        val focusAreas = buildList {
            if (matchedKeywords.isNotEmpty()) add("핵심 키워드 ${matchedKeywords.take(3).joinToString(", ")} 경험을 상단에 배치하세요.")
            if (resume.topProjectTitles.isNotEmpty()) add("프로젝트 ${resume.topProjectTitles.first()}를 대표 성과 사례로 강조하세요.")
            if (missingKeywords.isNotEmpty()) add("누락 키워드 ${missingKeywords.take(3).joinToString(", ")}와 연결되는 경험을 보강하세요.")
        }.distinct()

        val keywordScore = when {
            postingKeywords.isEmpty() -> 55.0
            else -> (matchedKeywords.size.toDouble() / postingKeywords.size.toDouble()) * 70.0
        }
        val quantScore = if (resume.quantifiedEvidenceCount > 0) 15.0 else 0.0
        val riskPenalty = (resume.riskSignals.size * 5.0).coerceAtMost(20.0)
        val overallScore = (keywordScore + quantScore + 20.0 - riskPenalty).toInt().coerceIn(0, 100)

        val recommendedFormatType = preferredFormatType
            ?: when {
                postingKeywords.any { it.contains("project", ignoreCase = true) || it.contains("architecture", ignoreCase = true) } -> "project_focused"
                postingKeywords.any { it.contains("skill", ignoreCase = true) || it.contains("kotlin", ignoreCase = true) || it.contains("spring", ignoreCase = true) } -> "technical_focused"
                else -> "experience_focused"
            }

        val suggestedHeadline = buildString {
            append(posting.roleName ?: resume.headlineSource ?: "Backend engineer")
            if (matchedKeywords.isNotEmpty()) {
                append(" | ")
                append(matchedKeywords.take(3).joinToString(" / "))
            }
        }

        val suggestedSummary = buildString {
            append(resume.summarySource ?: "운영과 문제 해결 경험을 가진 개발자입니다.")
            if (matchedKeywords.isNotEmpty()) {
                append(" ")
                append("특히 ${matchedKeywords.take(3).joinToString(", ")} 관련 경험을 중심으로 포지션 적합도를 강화할 수 있습니다.")
            }
            if (missingKeywords.isNotEmpty()) {
                append(" ")
                append("현재 이력서에서는 ${missingKeywords.take(2).joinToString(", ")} 관련 표현이 약합니다.")
            }
        }

        val suggestions = buildList {
            add(
                GeneratedSuggestion(
                    sectionKey = "headline",
                    originalText = resume.headlineSource,
                    suggestedText = suggestedHeadline,
                    reason = "지원 포지션과 직접 연결되는 핵심 키워드를 헤드라인에 먼저 노출하면 ATS와 채용담당자 모두에게 더 빠르게 맥락을 전달할 수 있습니다.",
                    suggestionType = "headline_rewrite",
                ),
            )
            add(
                GeneratedSuggestion(
                    sectionKey = "summary",
                    originalText = resume.summarySource,
                    suggestedText = suggestedSummary,
                    reason = "요약문에 포지션 적합성과 운영/성과 중심 표현을 함께 넣어 첫 스크린에서 강점을 명확히 드러내야 합니다.",
                    suggestionType = "summary_rewrite",
                ),
            )
            if (resume.topProjectTitles.isNotEmpty()) {
                add(
                    GeneratedSuggestion(
                        sectionKey = "projects",
                        originalText = resume.topProjectTitles.joinToString(", "),
                        suggestedText = "${resume.topProjectTitles.first()} 프로젝트를 상단으로 올리고, 사용 기술과 성과 지표를 한 문장으로 바로 이어서 배치하세요.",
                        reason = "공고와 맞닿아 있는 프로젝트를 먼저 노출하면 경력보다 빠르게 적합한 경험을 설명할 수 있습니다.",
                        suggestionType = "project_emphasis",
                    ),
                )
            }
            if (missingKeywords.isNotEmpty()) {
                add(
                    GeneratedSuggestion(
                        sectionKey = "skills",
                        originalText = resume.resumeKeywords.joinToString(", ").takeIf { it.isNotBlank() },
                        suggestedText = "보유 경험 안에서 ${missingKeywords.take(3).joinToString(", ")}와 연결되는 기술 또는 역할 표현을 명시적으로 추가하세요.",
                        reason = "경험은 있어도 키워드가 이력서에 노출되지 않으면 매칭 점수가 낮게 해석될 수 있습니다.",
                        suggestionType = "keyword_gap",
                    ),
                )
            }
            if (resume.quantifiedEvidenceCount == 0) {
                add(
                    GeneratedSuggestion(
                        sectionKey = "achievements",
                        originalText = null,
                        suggestedText = "주요 프로젝트마다 응답속도, 처리량, 비용, 배포시간 같은 수치형 결과를 최소 1개 이상 넣어 성과를 정량화하세요.",
                        reason = "현재 이력서에는 성과를 수치로 증명하는 문장이 거의 없어 설득력이 약해질 수 있습니다.",
                        suggestionType = "quantification",
                    ),
                )
            }
        }

        val matchSummary = buildString {
            append("현재 이력서는 ")
            append(posting.roleName ?: "대상 포지션")
            append(" 기준으로 ")
            append(overallScore)
            append("점 수준의 적합도를 보입니다. ")
            if (matchedKeywords.isNotEmpty()) {
                append("강하게 맞는 키워드는 ${matchedKeywords.take(4).joinToString(", ")}입니다. ")
            }
            if (missingKeywords.isNotEmpty()) {
                append("보완이 필요한 키워드는 ${missingKeywords.take(4).joinToString(", ")}입니다.")
            }
        }.trim()

        return GeneratedResumeAnalysis(
            overallScore = overallScore,
            matchSummary = matchSummary,
            strongMatches = matchedKeywords.take(8),
            missingKeywords = missingKeywords,
            weakSignals = weakSignals,
            recommendedFocusAreas = focusAreas,
            suggestedHeadline = suggestedHeadline,
            suggestedSummary = suggestedSummary,
            recommendedFormatType = recommendedFormatType,
            suggestions = suggestions,
        )
    }

    private fun decodeStringList(raw: String): List<String> =
        runCatching { objectMapper.readValue(raw, object : TypeReference<List<String>>() {}) }
            .getOrDefault(emptyList())

    private fun extractInlineKeywords(line: String): List<String> =
        Regex("""[A-Za-z][A-Za-z0-9+.#/-]{2,}""").findAll(line).map { it.value }.toList()

    private companion object {
        private const val STATUS_COMPLETED = "completed"
    }
}

private data class ResumeSnapshot(
    val headlineSource: String?,
    val summarySource: String?,
    val resumeKeywords: List<String>,
    val quantifiedEvidenceCount: Int,
    val riskSignals: List<String>,
    val topProjectTitles: List<String>,
    val topExperienceTitles: List<String>,
)

private data class JobPostingSnapshot(
    val keywords: List<String>,
    val requirements: List<String>,
    val responsibilities: List<String>,
    val companyName: String?,
    val roleName: String?,
)

private data class GeneratedResumeAnalysis(
    val overallScore: Int,
    val matchSummary: String,
    val strongMatches: List<String>,
    val missingKeywords: List<String>,
    val weakSignals: List<String>,
    val recommendedFocusAreas: List<String>,
    val suggestedHeadline: String?,
    val suggestedSummary: String?,
    val recommendedFormatType: String?,
    val suggestions: List<GeneratedSuggestion>,
)

private data class GeneratedSuggestion(
    val sectionKey: String,
    val originalText: String?,
    val suggestedText: String,
    val reason: String,
    val suggestionType: String,
)
