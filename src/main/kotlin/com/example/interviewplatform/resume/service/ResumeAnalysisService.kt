package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.jobposting.repository.JobPostingRepository
import com.example.interviewplatform.resume.dto.CreateResumeAnalysisExportRequest
import com.example.interviewplatform.resume.dto.CreateResumeAnalysisRequest
import com.example.interviewplatform.resume.dto.ResumeAnalysisDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisExportDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisListItemDto
import com.example.interviewplatform.resume.dto.ResumeTailoredDocumentDto
import com.example.interviewplatform.resume.dto.ResumeTailoredDocumentSectionDto
import com.example.interviewplatform.resume.dto.UpdateResumeAnalysisSuggestionRequest
import com.example.interviewplatform.resume.entity.ResumeAnalysisEntity
import com.example.interviewplatform.resume.entity.ResumeAnalysisExportEntity
import com.example.interviewplatform.resume.entity.ResumeAnalysisSuggestionEntity
import com.example.interviewplatform.resume.mapper.ResumeAnalysisMapper
import com.example.interviewplatform.resume.repository.ResumeAnalysisExportRepository
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
import org.springframework.core.io.Resource
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
    private val resumeAnalysisExportRepository: ResumeAnalysisExportRepository,
    private val resumeAnalysisGenerationClient: ResumeAnalysisGenerationClient,
    private val resumeAnalysisPdfExportService: ResumeAnalysisPdfExportService,
    private val resumeAnalysisExportFileStorageService: ResumeAnalysisExportFileStorageService,
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
        return toDetailDto(analysis, versionId)
    }

    @Transactional
    fun createAnalysis(userId: Long, versionId: Long, request: CreateResumeAnalysisRequest): ResumeAnalysisDto {
        val version = requireOwnedVersion(userId, versionId)
        val jobPosting = request.jobPostingId?.let { jobPostingId ->
            jobPostingRepository.findByIdAndUserId(jobPostingId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found: $jobPostingId")
        }
        val resumeSnapshot = buildResumeSnapshot(version.id)
        val postingSnapshot = buildJobPostingSnapshot(jobPosting)
        val deterministic = generateDeterministicAnalysis(resumeSnapshot, postingSnapshot, request.preferredFormatType)
        val generated = enrichAnalysis(deterministic, resumeSnapshot, postingSnapshot)

        val now = clockService.now()
        val savedAnalysis = resumeAnalysisRepository.save(
            ResumeAnalysisEntity(
                userId = userId,
                resumeVersionId = version.id,
                jobPostingId = jobPosting?.id,
                status = STATUS_COMPLETED,
                overallScore = deterministic.overallScore,
                matchSummary = generated.matchSummary,
                strongMatchesJson = objectMapper.writeValueAsString(deterministic.strongMatches),
                missingKeywordsJson = objectMapper.writeValueAsString(deterministic.missingKeywords),
                weakSignalsJson = objectMapper.writeValueAsString(deterministic.weakSignals),
                recommendedFocusAreasJson = objectMapper.writeValueAsString(deterministic.recommendedFocusAreas),
                suggestedHeadline = generated.suggestedHeadline,
                suggestedSummary = generated.suggestedSummary,
                recommendedFormatType = generated.recommendedFormatType,
                generationSource = generated.generationSource,
                llmModel = generated.llmModel,
                tailoredContentJson = objectMapper.writeValueAsString(generated.tailoredDocument.sections.map(::toPersistedSection)),
                tailoredPlainText = generated.tailoredDocument.plainText,
                sectionOrderJson = objectMapper.writeValueAsString(generated.tailoredDocument.sectionOrder),
                diffSummary = generated.diffSummary,
                analysisNotesJson = objectMapper.writeValueAsString(generated.analysisNotes),
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
        return toDetailDto(savedAnalysis, versionId)
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
        val refreshed = refreshTailoredDocument(analysis, now)
        return toDetailDto(refreshed, versionId)
    }

    @Transactional(readOnly = true)
    fun listExports(userId: Long, versionId: Long, analysisId: Long): List<ResumeAnalysisExportDto> {
        requireOwnedVersion(userId, versionId)
        val analysis = requireOwnedAnalysis(userId, analysisId)
        if (analysis.resumeVersionId != versionId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis not found: $analysisId")
        }
        return resumeAnalysisExportRepository.findByResumeAnalysisIdOrderByCreatedAtDescIdDesc(analysis.id)
            .map { ResumeAnalysisMapper.toExportDto(it, versionId, analysisId) }
    }

    @Transactional
    fun createExport(
        userId: Long,
        versionId: Long,
        analysisId: Long,
        request: CreateResumeAnalysisExportRequest,
    ): ResumeAnalysisExportDto {
        requireOwnedVersion(userId, versionId)
        val analysis = requireOwnedAnalysis(userId, analysisId)
        if (analysis.resumeVersionId != versionId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis not found: $analysisId")
        }
        if (request.exportType != "pdf") {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported exportType: ${request.exportType}")
        }
        val tailoredDocument = requireTailoredDocument(analysis)
        val exported = resumeAnalysisPdfExportService.export(tailoredDocument)
        val now = clockService.now()
        val companySlug = tailoredDocument.targetCompany?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-')
        val fileName = buildString {
            append(companySlug ?: "tailored-resume")
            append("-analysis-")
            append(analysis.id)
            append(".pdf")
        }
        val stored = resumeAnalysisExportFileStorageService.store(
            userId = userId,
            analysisId = analysis.id,
            fileName = fileName,
            content = exported.content,
            now = now,
        )
        val saved = resumeAnalysisExportRepository.save(
            ResumeAnalysisExportEntity(
                userId = userId,
                resumeAnalysisId = analysis.id,
                exportType = request.exportType,
                formatType = analysis.recommendedFormatType,
                fileName = fileName,
                storageKey = stored.storageKey,
                fileSizeBytes = stored.fileSizeBytes,
                checksumSha256 = stored.checksumSha256,
                pageCount = exported.pageCount,
                createdAt = now,
            ),
        )
        return ResumeAnalysisMapper.toExportDto(saved, versionId, analysisId)
    }

    @Transactional(readOnly = true)
    fun downloadExport(
        userId: Long,
        versionId: Long,
        analysisId: Long,
        exportId: Long,
    ): DownloadedResumeAnalysisExport {
        requireOwnedVersion(userId, versionId)
        val analysis = requireOwnedAnalysis(userId, analysisId)
        if (analysis.resumeVersionId != versionId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis not found: $analysisId")
        }
        val export = resumeAnalysisExportRepository.findByIdAndResumeAnalysisId(exportId, analysis.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis export not found: $exportId")
        return DownloadedResumeAnalysisExport(
            fileName = export.fileName,
            contentType = "application/pdf",
            resource = resumeAnalysisExportFileStorageService.load(export.storageKey),
        )
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

    private fun toDetailDto(entity: ResumeAnalysisEntity, versionId: Long): ResumeAnalysisDto {
        val suggestions = resumeAnalysisSuggestionRepository.findByResumeAnalysisIdOrderByDisplayOrderAscIdAsc(entity.id)
            .map(ResumeAnalysisMapper::toSuggestionDto)
        val exports = resumeAnalysisExportRepository.findByResumeAnalysisIdOrderByCreatedAtDescIdDesc(entity.id)
            .map { ResumeAnalysisMapper.toExportDto(it, versionId, entity.id) }
        return ResumeAnalysisMapper.toDetailDto(
            entity = entity,
            strongMatches = decodeStringList(entity.strongMatchesJson),
            missingKeywords = decodeStringList(entity.missingKeywordsJson),
            weakSignals = decodeStringList(entity.weakSignalsJson),
            recommendedFocusAreas = decodeStringList(entity.recommendedFocusAreasJson),
            analysisNotes = decodeStringList(entity.analysisNotesJson),
            tailoredDocument = entity.toTailoredDocument(
                suggestions = suggestions,
                targetCompany = entity.jobPostingId?.let { jobPostingRepository.findById(it).orElse(null)?.companyName },
                targetRole = entity.jobPostingId?.let { jobPostingRepository.findById(it).orElse(null)?.roleName },
            ),
            suggestions = suggestions,
            exports = exports,
        )
    }

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

        val experienceHighlights = experiences.mapNotNull { experience ->
            val label = listOfNotNull(experience.roleName, experience.companyName).joinToString(" @ ").trim()
            label.takeIf { it.isNotBlank() }
        }
        val projectHighlights = projects.map { project ->
            buildString {
                append(project.title)
                project.contentText?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it.take(160))
                }
            }
        }

        return ResumeSnapshot(
            headlineSource = profile?.headline ?: profile?.fullName ?: experiences.firstOrNull()?.roleName,
            summarySource = profile?.summaryText ?: competencies.firstOrNull()?.description ?: experiences.firstOrNull()?.summaryText,
            resumeKeywords = resumeKeywords,
            quantifiedEvidenceCount = (experiences.mapNotNull { it.summaryText } + projects.mapNotNull { it.contentText })
                .count { it.contains(Regex("\\d")) },
            riskSignals = risks.map { it.riskType.replace('_', ' ').lowercase() },
            topProjectTitles = projects.map { it.title }.take(3),
            topExperienceTitles = experienceHighlights.take(3),
            experienceHighlights = experienceHighlights,
            projectHighlights = projectHighlights,
            skillHighlights = skills.map { it.skillName }.take(12),
        )
    }

    private fun buildJobPostingSnapshot(jobPosting: com.example.interviewplatform.jobposting.entity.JobPostingEntity?): JobPostingSnapshot =
        jobPosting?.let { posting ->
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

    private fun generateDeterministicAnalysis(
        resume: ResumeSnapshot,
        posting: JobPostingSnapshot,
        preferredFormatType: String?,
    ): DeterministicResumeAnalysis {
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
                append(" 특히 ${matchedKeywords.take(3).joinToString(", ")} 관련 경험을 중심으로 포지션 적합도를 강화할 수 있습니다.")
            }
            if (missingKeywords.isNotEmpty()) {
                append(" 현재 이력서에서는 ${missingKeywords.take(2).joinToString(", ")} 관련 표현이 약합니다.")
            }
        }

        val suggestions = buildList {
            add(
                ResumeAnalysisSuggestionSeed(
                    sectionKey = "headline",
                    originalText = resume.headlineSource,
                    suggestedText = suggestedHeadline,
                    reason = "지원 포지션과 직접 연결되는 핵심 키워드를 헤드라인에 먼저 노출하면 ATS와 채용담당자 모두에게 더 빠르게 맥락을 전달할 수 있습니다.",
                    suggestionType = "headline_rewrite",
                ),
            )
            add(
                ResumeAnalysisSuggestionSeed(
                    sectionKey = "summary",
                    originalText = resume.summarySource,
                    suggestedText = suggestedSummary,
                    reason = "요약문에 포지션 적합성과 운영/성과 중심 표현을 함께 넣어 첫 스크린에서 강점을 명확히 드러내야 합니다.",
                    suggestionType = "summary_rewrite",
                ),
            )
            if (resume.topProjectTitles.isNotEmpty()) {
                add(
                    ResumeAnalysisSuggestionSeed(
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
                    ResumeAnalysisSuggestionSeed(
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
                    ResumeAnalysisSuggestionSeed(
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

        val sectionOrder = sectionOrderForFormat(recommendedFormatType)
        val analysisNotes = buildList {
            if (matchedKeywords.isNotEmpty()) add("공고 핵심 키워드 ${matchedKeywords.take(3).joinToString(", ")}를 상단 요약과 프로젝트 서두에 반복 노출하세요.")
            if (missingKeywords.isNotEmpty()) add("누락 키워드 ${missingKeywords.take(3).joinToString(", ")}는 허위 추가가 아니라 실제 경험과 연결된 표현으로만 보강해야 합니다.")
        }
        val diffSummary = buildString {
            append("원본 이력서의 핵심 경험은 유지하면서 ")
            append(sectionOrder.joinToString(" → "))
            append(" 순서로 재배치했습니다.")
        }
        val tailoredDocument = buildTailoredDocument(
            companyName = posting.companyName,
            roleName = posting.roleName,
            formatType = recommendedFormatType,
            headline = suggestedHeadline,
            summary = suggestedSummary,
            sectionOrder = sectionOrder,
            resume = resume,
            suggestions = suggestions,
            analysisNotes = analysisNotes,
            diffSummary = diffSummary,
        )

        return DeterministicResumeAnalysis(
            overallScore = overallScore,
            matchSummary = matchSummary,
            strongMatches = matchedKeywords.take(8),
            missingKeywords = missingKeywords,
            weakSignals = weakSignals,
            recommendedFocusAreas = focusAreas,
            suggestedHeadline = suggestedHeadline,
            suggestedSummary = suggestedSummary,
            recommendedFormatType = recommendedFormatType,
            analysisNotes = analysisNotes,
            diffSummary = diffSummary,
            suggestions = suggestions,
            sectionOrder = sectionOrder,
            tailoredDocument = tailoredDocument,
        )
    }

    private fun enrichAnalysis(
        deterministic: DeterministicResumeAnalysis,
        resume: ResumeSnapshot,
        posting: JobPostingSnapshot,
    ): ResumeAnalysisGenerationResult {
        if (!resumeAnalysisGenerationClient.isEnabled()) {
            return ResumeAnalysisGenerationResult(
                matchSummary = deterministic.matchSummary,
                suggestedHeadline = deterministic.suggestedHeadline,
                suggestedSummary = deterministic.suggestedSummary,
                recommendedFormatType = deterministic.recommendedFormatType,
                analysisNotes = deterministic.analysisNotes,
                diffSummary = deterministic.diffSummary,
                suggestions = deterministic.suggestions,
                tailoredDocument = deterministic.tailoredDocument,
                generationSource = "deterministic",
                llmModel = null,
            )
        }
        val input = ResumeAnalysisGenerationInput(
            companyName = posting.companyName,
            roleName = posting.roleName,
            resumeKeywords = resume.resumeKeywords,
            postingKeywords = posting.keywords,
            strongMatches = deterministic.strongMatches,
            missingKeywords = deterministic.missingKeywords,
            weakSignals = deterministic.weakSignals,
            recommendedFocusAreas = deterministic.recommendedFocusAreas,
            suggestedHeadline = deterministic.suggestedHeadline,
            suggestedSummary = deterministic.suggestedSummary,
            recommendedFormatType = deterministic.recommendedFormatType,
            suggestions = deterministic.suggestions,
            preferredSectionOrder = deterministic.sectionOrder,
            topExperienceTitles = resume.topExperienceTitles,
            topProjectTitles = resume.topProjectTitles,
        )
        return runCatching { resumeAnalysisGenerationClient.generate(input) }
            .getOrElse {
                ResumeAnalysisGenerationResult(
                    matchSummary = deterministic.matchSummary,
                    suggestedHeadline = deterministic.suggestedHeadline,
                    suggestedSummary = deterministic.suggestedSummary,
                    recommendedFormatType = deterministic.recommendedFormatType,
                    analysisNotes = deterministic.analysisNotes,
                    diffSummary = deterministic.diffSummary,
                    suggestions = deterministic.suggestions,
                    tailoredDocument = deterministic.tailoredDocument,
                    generationSource = "deterministic",
                    llmModel = null,
                )
            }
    }

    private fun refreshTailoredDocument(analysis: ResumeAnalysisEntity, now: java.time.Instant): ResumeAnalysisEntity {
        val suggestions = resumeAnalysisSuggestionRepository.findByResumeAnalysisIdOrderByDisplayOrderAscIdAsc(analysis.id)
        val acceptedSections = decodePersistedSections(analysis.tailoredContentJson).map { section ->
            val matchingSuggestions = suggestions.filter { it.sectionKey == section.sectionKey && it.accepted }
            if (matchingSuggestions.isEmpty()) {
                section
            } else {
                TailoredResumeSection(
                    sectionKey = section.sectionKey,
                    title = section.title,
                    lines = matchingSuggestions.map { it.suggestedText } + section.lines.drop(1),
                )
            }
        }
        val plainText = acceptedSections.joinToString("\n\n") { section ->
            buildString {
                append(section.title)
                append('\n')
                append(section.lines.joinToString("\n"))
            }
        }
        val refreshed = ResumeAnalysisEntity(
            id = analysis.id,
            userId = analysis.userId,
            resumeVersionId = analysis.resumeVersionId,
            jobPostingId = analysis.jobPostingId,
            status = analysis.status,
            overallScore = analysis.overallScore,
            matchSummary = analysis.matchSummary,
            strongMatchesJson = analysis.strongMatchesJson,
            missingKeywordsJson = analysis.missingKeywordsJson,
            weakSignalsJson = analysis.weakSignalsJson,
            recommendedFocusAreasJson = analysis.recommendedFocusAreasJson,
            suggestedHeadline = analysis.suggestedHeadline,
            suggestedSummary = analysis.suggestedSummary,
            recommendedFormatType = analysis.recommendedFormatType,
            generationSource = analysis.generationSource,
            llmModel = analysis.llmModel,
            tailoredContentJson = objectMapper.writeValueAsString(acceptedSections.map(::toPersistedSection)),
            tailoredPlainText = plainText,
            sectionOrderJson = analysis.sectionOrderJson,
            diffSummary = analysis.diffSummary,
            analysisNotesJson = analysis.analysisNotesJson,
            createdAt = analysis.createdAt,
            updatedAt = now,
        )
        return resumeAnalysisRepository.save(refreshed)
    }

    private fun requireTailoredDocument(analysis: ResumeAnalysisEntity): TailoredResumeDocument =
        analysis.toTailoredDocument(
            suggestions = resumeAnalysisSuggestionRepository.findByResumeAnalysisIdOrderByDisplayOrderAscIdAsc(analysis.id)
                .map(ResumeAnalysisMapper::toSuggestionDto),
            targetCompany = analysis.jobPostingId?.let { jobPostingRepository.findById(it).orElse(null)?.companyName },
            targetRole = analysis.jobPostingId?.let { jobPostingRepository.findById(it).orElse(null)?.roleName },
        )?.let(::toServiceDocument)
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Resume analysis does not have a tailored document")

    private fun ResumeAnalysisEntity.toTailoredDocument(
        suggestions: List<com.example.interviewplatform.resume.dto.ResumeAnalysisSuggestionDto>,
        targetCompany: String?,
        targetRole: String?,
    ): ResumeTailoredDocumentDto? {
        val sections = decodePersistedSections(tailoredContentJson)
        if (sections.isEmpty()) {
            return null
        }
        return ResumeTailoredDocumentDto(
            title = suggestedHeadline ?: targetRole ?: "Tailored Resume",
            targetCompany = targetCompany,
            targetRole = targetRole,
            formatType = recommendedFormatType ?: "experience_focused",
            sectionOrder = decodeStringList(sectionOrderJson),
            summary = suggestedSummary,
            diffSummary = diffSummary,
            analysisNotes = decodeStringList(analysisNotesJson),
            sections = sections.map { section ->
                ResumeTailoredDocumentSectionDto(
                    sectionKey = section.sectionKey,
                    title = section.title,
                    lines = section.lines,
                )
            },
            plainText = tailoredPlainText,
        )
    }

    private fun buildTailoredDocument(
        companyName: String?,
        roleName: String?,
        formatType: String,
        headline: String?,
        summary: String?,
        sectionOrder: List<String>,
        resume: ResumeSnapshot,
        suggestions: List<ResumeAnalysisSuggestionSeed>,
        analysisNotes: List<String>,
        diffSummary: String?,
    ): TailoredResumeDocument {
        val sectionMap = linkedMapOf(
            "summary" to TailoredResumeSection("summary", "Summary", listOfNotNull(summary)),
            "experience" to TailoredResumeSection("experience", "Experience", resume.experienceHighlights.ifEmpty { listOf("핵심 경력 항목을 보강하세요.") }),
            "projects" to TailoredResumeSection("projects", "Projects", resume.projectHighlights.ifEmpty { listOf("대표 프로젝트를 먼저 배치하세요.") }),
            "skills" to TailoredResumeSection("skills", "Skills", listOf(resume.skillHighlights.joinToString(", ").ifBlank { "핵심 기술을 명시하세요." })),
            "achievements" to TailoredResumeSection("achievements", "Achievements", suggestions.filter { it.sectionKey == "achievements" }.map { it.suggestedText }.ifEmpty { listOf("정량 성과를 프로젝트별로 보강하세요.") }),
        )
        val orderedSections = sectionOrder.mapNotNull(sectionMap::get) + sectionMap.filterKeys { it !in sectionOrder }.values
        val plainText = buildString {
            appendLine(headline ?: roleName ?: "Tailored Resume")
            orderedSections.forEach { section ->
                appendLine()
                appendLine(section.title)
                section.lines.forEach { appendLine("- $it") }
            }
        }.trim()
        return TailoredResumeDocument(
            title = headline ?: roleName ?: "Tailored Resume",
            targetCompany = companyName,
            targetRole = roleName,
            formatType = formatType,
            sectionOrder = orderedSections.map { it.sectionKey },
            summary = summary,
            diffSummary = diffSummary,
            analysisNotes = analysisNotes,
            sections = orderedSections,
            plainText = plainText,
        )
    }

    private fun decodePersistedSections(raw: String?): List<TailoredResumeSection> =
        runCatching {
            objectMapper.readValue(raw.orEmpty(), object : TypeReference<List<PersistedTailoredSection>>() {})
        }.getOrDefault(emptyList())
            .map { section ->
                TailoredResumeSection(
                    sectionKey = section.sectionKey,
                    title = section.title,
                    lines = section.lines,
                )
            }

    private fun toPersistedSection(section: TailoredResumeSection): PersistedTailoredSection =
        PersistedTailoredSection(
            sectionKey = section.sectionKey,
            title = section.title,
            lines = section.lines,
        )

    private fun toServiceDocument(dto: ResumeTailoredDocumentDto): TailoredResumeDocument =
        TailoredResumeDocument(
            title = dto.title,
            targetCompany = dto.targetCompany,
            targetRole = dto.targetRole,
            formatType = dto.formatType,
            sectionOrder = dto.sectionOrder,
            summary = dto.summary,
            diffSummary = dto.diffSummary,
            analysisNotes = dto.analysisNotes,
            sections = dto.sections.map { section ->
                TailoredResumeSection(
                    sectionKey = section.sectionKey,
                    title = section.title,
                    lines = section.lines,
                )
            },
            plainText = dto.plainText.orEmpty(),
        )

    private fun decodeStringList(raw: String): List<String> =
        runCatching { objectMapper.readValue(raw, object : TypeReference<List<String>>() {}) }
            .getOrDefault(emptyList())

    private fun extractInlineKeywords(line: String): List<String> =
        Regex("""[A-Za-z][A-Za-z0-9+.#/-]{2,}""").findAll(line).map { it.value }.toList()

    private fun sectionOrderForFormat(formatType: String): List<String> = when (formatType) {
        "project_focused" -> listOf("summary", "projects", "experience", "skills", "achievements")
        "technical_focused" -> listOf("summary", "skills", "experience", "projects", "achievements")
        "concise" -> listOf("summary", "experience", "skills")
        else -> listOf("summary", "experience", "projects", "skills", "achievements")
    }

    private companion object {
        private const val STATUS_COMPLETED = "completed"
    }
}

data class DownloadedResumeAnalysisExport(
    val fileName: String,
    val contentType: String,
    val resource: Resource,
)

private data class ResumeSnapshot(
    val headlineSource: String?,
    val summarySource: String?,
    val resumeKeywords: List<String>,
    val quantifiedEvidenceCount: Int,
    val riskSignals: List<String>,
    val topProjectTitles: List<String>,
    val topExperienceTitles: List<String>,
    val experienceHighlights: List<String>,
    val projectHighlights: List<String>,
    val skillHighlights: List<String>,
)

private data class JobPostingSnapshot(
    val keywords: List<String>,
    val requirements: List<String>,
    val responsibilities: List<String>,
    val companyName: String?,
    val roleName: String?,
)

private data class DeterministicResumeAnalysis(
    val overallScore: Int,
    val matchSummary: String,
    val strongMatches: List<String>,
    val missingKeywords: List<String>,
    val weakSignals: List<String>,
    val recommendedFocusAreas: List<String>,
    val suggestedHeadline: String?,
    val suggestedSummary: String?,
    val recommendedFormatType: String,
    val analysisNotes: List<String>,
    val diffSummary: String?,
    val suggestions: List<ResumeAnalysisSuggestionSeed>,
    val sectionOrder: List<String>,
    val tailoredDocument: TailoredResumeDocument,
)

private data class PersistedTailoredSection(
    val sectionKey: String,
    val title: String,
    val lines: List<String>,
)
