package com.example.interviewplatform.interview.service

import com.example.interviewplatform.answer.dto.SubmitAnswerRequest
import com.example.interviewplatform.answer.repository.AnswerScoreRepository
import com.example.interviewplatform.answer.service.AnswerProgressSource
import com.example.interviewplatform.answer.service.AnswerService
import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.interview.dto.CreateInterviewSessionRequest
import com.example.interviewplatform.interview.dto.InterviewSessionAdvanceResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionAnswerResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionCoverageEvidenceItemDto
import com.example.interviewplatform.interview.dto.InterviewSessionCoverageResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionDetailResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionListItemDto
import com.example.interviewplatform.interview.dto.InterviewResumeEvidenceDto
import com.example.interviewplatform.interview.dto.InterviewSessionResumeMapEvidenceItemDto
import com.example.interviewplatform.interview.dto.InterviewSessionResumeMapQuestionDto
import com.example.interviewplatform.interview.dto.InterviewSessionResumeMapResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionQuestionDto
import com.example.interviewplatform.interview.dto.SubmitInterviewSessionAnswerRequest
import com.example.interviewplatform.interview.dto.SkipInterviewSessionQuestionRequest
import com.example.interviewplatform.interview.entity.InterviewSessionEvidenceItemEntity
import com.example.interviewplatform.interview.entity.InterviewSessionEntity
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEvidenceLinkEntity
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEvidenceLinkId
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.interview.mapper.InterviewSessionMapper
import com.example.interviewplatform.interview.repository.InterviewSessionEvidenceItemRepository
import com.example.interviewplatform.interview.repository.InterviewSessionQuestionEvidenceLinkRepository
import com.example.interviewplatform.interview.repository.InterviewSessionQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewSessionRepository
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.question.repository.CategoryRepository
import com.example.interviewplatform.question.repository.QuestionRelationshipRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.QuestionSkillMappingRepository
import com.example.interviewplatform.question.repository.QuestionTagRepository
import com.example.interviewplatform.question.repository.TagRepository
import com.example.interviewplatform.question.service.QuestionService
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import com.example.interviewplatform.skill.repository.SkillRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class InterviewSessionService(
    private val interviewSessionRepository: InterviewSessionRepository,
    private val interviewSessionQuestionRepository: InterviewSessionQuestionRepository,
    private val interviewSessionEvidenceItemRepository: InterviewSessionEvidenceItemRepository,
    private val interviewSessionQuestionEvidenceLinkRepository: InterviewSessionQuestionEvidenceLinkRepository,
    private val questionRepository: QuestionRepository,
    private val questionRelationshipRepository: QuestionRelationshipRepository,
    private val questionSkillMappingRepository: QuestionSkillMappingRepository,
    private val questionTagRepository: QuestionTagRepository,
    private val tagRepository: TagRepository,
    private val categoryRepository: CategoryRepository,
    private val questionService: QuestionService,
    private val answerService: AnswerService,
    private val answerScoreRepository: AnswerScoreRepository,
    private val reviewQueueRepository: ReviewQueueRepository,
    private val resumeRepository: ResumeRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val skillRepository: SkillRepository,
    private val interviewResumeEvidenceAssembler: InterviewResumeEvidenceAssembler,
    private val interviewOpeningGenerationService: InterviewOpeningGenerationService,
    private val interviewFollowUpGenerationService: InterviewFollowUpGenerationService,
    private val appLocaleService: AppLocaleService,
    private val clockService: ClockService,
    private val objectMapper: ObjectMapper,
    @Value("\${app.interview.follow-up.max-depth:2}")
    private val maxFollowUpDepth: Int,
) {
    @Transactional(readOnly = true)
    fun listSessions(userId: Long): List<InterviewSessionListItemDto> {
        val sessions = interviewSessionRepository.findByUserIdOrderByStartedAtDesc(userId)
        if (sessions.isEmpty()) {
            return emptyList()
        }

        val rowsBySessionId = interviewSessionQuestionRepository
            .findByInterviewSessionIdInOrderByInterviewSessionIdAscOrderIndexAsc(sessions.map { it.id })
            .groupBy { it.interviewSessionId }

        return sessions.map { session ->
            val rows = rowsBySessionId[session.id].orEmpty()
            val summary = summarize(rows)
            InterviewSessionMapper.toListItemDto(
                id = session.id,
                sessionType = session.sessionType,
                interviewMode = session.interviewMode,
                status = session.status,
                resumeVersionId = session.resumeVersionId,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                questionCount = summary.totalQuestions,
                answeredCount = summary.answeredQuestions,
                averageScore = summary.averageScore,
            )
        }
    }

    @Transactional
    fun createSession(userId: Long, request: CreateInterviewSessionRequest): InterviewSessionDetailResponseDto {
        val now = clockService.now()
        val sessionType = normalizeSessionType(request.sessionType)
        val interviewMode = normalizeInterviewMode(request.interviewMode)
        val resumeVersionId = resolveResumeVersionId(userId, request.resumeVersionId, sessionType)

        val session = interviewSessionRepository.save(
            InterviewSessionEntity(
                userId = userId,
                resumeVersionId = resumeVersionId,
                sessionType = sessionType,
                interviewMode = interviewMode,
                status = STATUS_IN_PROGRESS,
                startedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val coverageItems = initializeCoverageInventory(session, resumeVersionId, now)
        val initialRows = buildInitialRows(
            userId = userId,
            session = session,
            requestedCount = request.questionCount,
            seedQuestionIds = request.seedQuestionIds,
            resumeVersionId = resumeVersionId,
            coverageItems = coverageItems,
            now = now,
        )
        if (initialRows.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No questions available for interview session")
        }
        val savedRows = interviewSessionQuestionRepository.saveAll(initialRows)
        syncCoverageLinks(session, savedRows, now)
        return getSession(userId, session.id)
    }

    @Transactional(readOnly = true)
    fun getSession(userId: Long, sessionId: Long): InterviewSessionDetailResponseDto {
        val session = requireSession(userId, sessionId)
        val rows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        return toDetailResponse(session, rows)
    }

    @Transactional(readOnly = true)
    fun getCoverage(userId: Long, sessionId: Long): InterviewSessionCoverageResponseDto {
        val session = requireSession(userId, sessionId)
        val evidenceItems = interviewSessionEvidenceItemRepository.findByInterviewSessionIdOrderByDisplayOrderAscIdAsc(session.id)
        val linkedQuestionIdsByEvidenceId = loadLinkedQuestionIdsByEvidenceId(evidenceItems.map { it.id })
        val total = evidenceItems.size
        val covered = evidenceItems.count { it.coverageStatus != COVERAGE_STATUS_UNASKED }
        val defended = evidenceItems.count { it.coverageStatus == COVERAGE_STATUS_DEFENDED }
        return InterviewSessionCoverageResponseDto(
            sessionId = session.id,
            interviewMode = session.interviewMode,
            overallCoveragePercent = percent(covered, total),
            defendedCoveragePercent = percent(defended, total),
            evidenceItems = evidenceItems.map { item ->
                InterviewSessionCoverageEvidenceItemDto(
                    id = item.id,
                    section = item.section,
                    label = item.label,
                    snippet = item.snippet,
                    facet = item.facet,
                    sourceRecordType = item.sourceRecordType,
                    sourceRecordId = item.sourceRecordId,
                    displayOrder = item.displayOrder,
                    coverageStatus = item.coverageStatus,
                    linkedQuestionIds = linkedQuestionIdsByEvidenceId[item.id].orEmpty(),
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun getResumeMap(userId: Long, sessionId: Long): InterviewSessionResumeMapResponseDto {
        val session = requireSession(userId, sessionId)
        val evidenceItems = interviewSessionEvidenceItemRepository.findByInterviewSessionIdOrderByDisplayOrderAscIdAsc(session.id)
        val links = interviewSessionQuestionEvidenceLinkRepository.findByIdInterviewSessionEvidenceItemIdIn(evidenceItems.map { it.id })
        val orderedRows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        val rowsById = orderedRows.associateBy { it.id }
        val currentRowId = orderedRows.firstOrNull { it.answerAttemptId == null && it.skippedAt == null }?.id
        val relatedQuestionsByEvidenceId = links.groupBy { it.id.interviewSessionEvidenceItemId }.mapValues { (_, groupedLinks) ->
            groupedLinks.mapNotNull { link ->
                rowsById[link.id.interviewSessionQuestionId]?.let { row ->
                    InterviewSessionResumeMapQuestionDto(
                        sessionQuestionId = row.id,
                        title = row.promptText ?: "",
                        sourceType = row.questionSourceType,
                        orderIndex = row.orderIndex,
                        status = questionStatus(row, currentRowId, session.status),
                        isFollowUp = row.isFollowUp,
                    )
                }
            }.sortedBy { question -> rowsById[question.sessionQuestionId]?.orderIndex }
        }
        return InterviewSessionResumeMapResponseDto(
            sessionId = session.id,
            resumeVersionId = session.resumeVersionId,
            evidenceItems = evidenceItems.map { item ->
                InterviewSessionResumeMapEvidenceItemDto(
                    section = item.section,
                    label = item.label,
                    snippet = item.snippet,
                    facet = item.facet,
                    sourceRecordType = item.sourceRecordType,
                    sourceRecordId = item.sourceRecordId,
                    displayOrder = item.displayOrder,
                    coverageStatus = item.coverageStatus,
                    primaryQuestionCount = relatedQuestionsByEvidenceId[item.id].orEmpty().count { !it.isFollowUp },
                    followUpQuestionCount = relatedQuestionsByEvidenceId[item.id].orEmpty().count { it.isFollowUp },
                    relatedQuestions = relatedQuestionsByEvidenceId[item.id].orEmpty(),
                )
            },
        )
    }

    @Transactional
    fun submitAnswer(
        userId: Long,
        sessionId: Long,
        request: SubmitInterviewSessionAnswerRequest,
    ): InterviewSessionAnswerResponseDto {
        val now = clockService.now()
        val session = requireSession(userId, sessionId)
        if (session.status != STATUS_IN_PROGRESS) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Interview session is not active: $sessionId")
        }

        val rows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        val row = rows.firstOrNull { it.id == request.sessionQuestionId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session question not found: ${request.sessionQuestionId}")
        if (row.answerAttemptId != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Interview session question already answered: ${row.id}")
        }
        if (row.skippedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Interview session question already skipped: ${row.id}")
        }

        val submission = answerService.submitAnswer(
            userId = userId,
            questionId = row.questionId
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Interview session question is not bound to a catalog question: ${row.id}"),
            request = SubmitAnswerRequest(
                answerMode = request.answerMode,
                contentText = request.contentText,
                resumeVersionId = request.resumeVersionId ?: session.resumeVersionId,
            ),
            progressSource = AnswerProgressSource(
                sourceType = SOURCE_TYPE_INTERVIEW,
                sourceLabel = SOURCE_LABEL_INTERVIEW,
                sourceSessionId = session.id,
                sourceSessionQuestionId = row.id,
                isFollowUp = row.isFollowUp,
            ),
        )
        interviewSessionQuestionRepository.save(
            InterviewSessionQuestionEntity(
                id = row.id,
                interviewSessionId = row.interviewSessionId,
                questionId = row.questionId,
                parentSessionQuestionId = row.parentSessionQuestionId,
                promptText = row.promptText,
                bodyText = row.bodyText,
                questionSourceType = row.questionSourceType,
                orderIndex = row.orderIndex,
                isFollowUp = row.isFollowUp,
                depth = row.depth,
                categoryName = row.categoryName,
                tagsJson = row.tagsJson,
                focusSkillNamesJson = row.focusSkillNamesJson,
                resumeContextSummary = row.resumeContextSummary,
                resumeEvidenceJson = row.resumeEvidenceJson,
                generationRationale = row.generationRationale,
                generationStatus = row.generationStatus,
                llmModel = row.llmModel,
                llmPromptVersion = row.llmPromptVersion,
                contentLocale = row.contentLocale,
                answerAttemptId = submission.answerAttemptId,
                skippedAt = row.skippedAt,
                createdAt = row.createdAt,
                updatedAt = now,
            ),
        )

        updateCoverageAfterAnswer(session, row, submission.scoreSummary.totalScore, now)

        val insertedFollowUp = maybeInsertFollowUp(
            userId = userId,
            session = session,
            sessionId = session.id,
            answeredRow = row,
            answerText = request.contentText,
            resumeVersionId = session.resumeVersionId,
            now = now,
        )
        if (!insertedFollowUp) {
            maybeInsertNextCoverageQuestion(userId = userId, session = session, now = now)
        }
        val refreshedRows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        val unresolvedNext = refreshedRows.firstOrNull { it.answerAttemptId == null }
        val updatedSession = if (unresolvedNext == null) {
            interviewSessionRepository.save(
                InterviewSessionEntity(
                    id = session.id,
                    userId = session.userId,
                    resumeVersionId = session.resumeVersionId,
                    sessionType = session.sessionType,
                    interviewMode = session.interviewMode,
                    status = STATUS_COMPLETED,
                    startedAt = session.startedAt,
                    endedAt = now,
                    createdAt = session.createdAt,
                    updatedAt = now,
                ),
            )
        } else {
            interviewSessionRepository.save(
                InterviewSessionEntity(
                    id = session.id,
                    userId = session.userId,
                    resumeVersionId = session.resumeVersionId,
                    sessionType = session.sessionType,
                    interviewMode = session.interviewMode,
                    status = STATUS_IN_PROGRESS,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    createdAt = session.createdAt,
                    updatedAt = now,
                ),
            )
        }

        val detail = toDetailResponse(updatedSession, refreshedRows)
        return InterviewSessionAnswerResponseDto(
            sessionId = updatedSession.id,
            sessionQuestionId = row.id,
            status = updatedSession.status,
            answer = submission,
            nextQuestion = detail.currentQuestion,
            summary = detail.summary,
        )
    }

    @Transactional
    fun skipQuestion(
        userId: Long,
        sessionId: Long,
        request: SkipInterviewSessionQuestionRequest,
    ): InterviewSessionAdvanceResponseDto {
        val now = clockService.now()
        val session = requireSession(userId, sessionId)
        if (session.status != STATUS_IN_PROGRESS) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Interview session is not active: $sessionId")
        }

        val rows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        val row = rows.firstOrNull { it.id == request.sessionQuestionId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session question not found: ${request.sessionQuestionId}")
        if (row.answerAttemptId != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Interview session question already answered: ${row.id}")
        }
        if (row.skippedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Interview session question already skipped: ${row.id}")
        }

        interviewSessionQuestionRepository.save(
            InterviewSessionQuestionEntity(
                id = row.id,
                interviewSessionId = row.interviewSessionId,
                questionId = row.questionId,
                parentSessionQuestionId = row.parentSessionQuestionId,
                promptText = row.promptText,
                bodyText = row.bodyText,
                questionSourceType = row.questionSourceType,
                orderIndex = row.orderIndex,
                isFollowUp = row.isFollowUp,
                depth = row.depth,
                categoryName = row.categoryName,
                tagsJson = row.tagsJson,
                focusSkillNamesJson = row.focusSkillNamesJson,
                resumeContextSummary = row.resumeContextSummary,
                resumeEvidenceJson = row.resumeEvidenceJson,
                generationRationale = row.generationRationale,
                generationStatus = row.generationStatus,
                llmModel = row.llmModel,
                llmPromptVersion = row.llmPromptVersion,
                contentLocale = row.contentLocale,
                answerAttemptId = row.answerAttemptId,
                skippedAt = now,
                createdAt = row.createdAt,
                updatedAt = now,
            ),
        )

        if (session.interviewMode == INTERVIEW_MODE_FULL_COVERAGE) {
            val links = interviewSessionQuestionEvidenceLinkRepository.findByIdInterviewSessionQuestionIdIn(listOf(row.id))
            links.forEach { link ->
                markCoverageStatus(link.id.interviewSessionEvidenceItemId, COVERAGE_STATUS_SKIPPED, now)
            }
        }

        return nextQuestion(userId, sessionId)
    }

    @Transactional
    fun nextQuestion(userId: Long, sessionId: Long): InterviewSessionAdvanceResponseDto {
        val session = requireSession(userId, sessionId)
        var rows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        var nextRow = rows.firstOrNull { it.answerAttemptId == null }
        if (nextRow?.skippedAt == null && nextRow != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Current question must be answered or skipped before advancing: ${nextRow.id}",
            )
        }
        nextRow = rows.firstOrNull { it.answerAttemptId == null && it.skippedAt == null }
        if (nextRow == null && session.status == STATUS_IN_PROGRESS && session.interviewMode == INTERVIEW_MODE_FULL_COVERAGE) {
            maybeInsertNextCoverageQuestion(userId = userId, session = session, now = clockService.now())
            rows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
            nextRow = rows.firstOrNull { it.answerAttemptId == null && it.skippedAt == null }
        }
        val effectiveSession = if (nextRow == null && session.status != STATUS_COMPLETED) {
            val now = clockService.now()
            interviewSessionRepository.save(
                InterviewSessionEntity(
                    id = session.id,
                    userId = session.userId,
                    resumeVersionId = session.resumeVersionId,
                    sessionType = session.sessionType,
                    interviewMode = session.interviewMode,
                    status = STATUS_COMPLETED,
                    startedAt = session.startedAt,
                    endedAt = now,
                    createdAt = session.createdAt,
                    updatedAt = now,
                ),
            )
        } else {
            session
        }
        val detail = toDetailResponse(effectiveSession, rows)
        return InterviewSessionAdvanceResponseDto(
            sessionId = effectiveSession.id,
            status = effectiveSession.status,
            currentQuestion = detail.currentQuestion,
            summary = detail.summary,
        )
    }

    private fun toDetailResponse(
        session: InterviewSessionEntity,
        rows: List<InterviewSessionQuestionEntity>,
    ): InterviewSessionDetailResponseDto {
        val questionIds = rows.mapNotNull { it.questionId }.distinct()
        val questionById = questionRepository.findAllById(questionIds).associateBy { it.id }
        val tagsByRowId = rows.associate { it.id to decodeJsonArray(it.tagsJson) }
        val focusSkillsByRowId = rows.associate { it.id to decodeJsonArray(it.focusSkillNamesJson) }
        val resumeEvidenceByRowId = rows.associate { it.id to decodeResumeEvidence(it.resumeEvidenceJson) }
        val currentRowId = rows.firstOrNull { it.answerAttemptId == null && it.skippedAt == null }?.id
        val questionDtos = rows.map { row ->
            val question = row.questionId?.let(questionById::get)
            InterviewSessionMapper.toQuestionDto(
                row = row,
                question = question,
                status = questionStatus(row, currentRowId, session.status),
                tags = tagsByRowId[row.id].orEmpty(),
                focusSkillNames = focusSkillsByRowId[row.id].orEmpty(),
                resumeEvidence = resumeEvidenceByRowId[row.id].orEmpty(),
            )
        }
        val summary = summarize(rows)
        return InterviewSessionDetailResponseDto(
            id = session.id,
            sessionType = session.sessionType,
            interviewMode = session.interviewMode,
            status = session.status,
            resumeVersionId = session.resumeVersionId,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            currentQuestion = questionDtos.firstOrNull { it.status == STATUS_CURRENT },
            questions = questionDtos,
            summary = summary,
        )
    }

    private fun summarize(rows: List<InterviewSessionQuestionEntity>) =
        InterviewSessionMapper.toSummaryDto(
            totalQuestions = rows.size,
            answeredQuestions = rows.count { it.answerAttemptId != null },
            skippedQuestions = rows.count { it.skippedAt != null },
            remainingQuestions = rows.count { it.answerAttemptId == null && it.skippedAt == null },
            averageScore = averageScore(rows),
        )

    private fun averageScore(rows: List<InterviewSessionQuestionEntity>): Double? {
        val attemptIds = rows.mapNotNull { it.answerAttemptId }
        if (attemptIds.isEmpty()) {
            return null
        }
        val totalScores = answerScoreRepository.findAllById(attemptIds).map { it.totalScore.toDouble() }
        if (totalScores.isEmpty()) {
            return null
        }
        return totalScores.average()
    }

    private fun buildSeedRows(
        sessionId: Long,
        questionIds: List<Long>,
        resumeVersionId: Long?,
        now: java.time.Instant,
    ): List<InterviewSessionQuestionEntity> {
        val questionsById = questionRepository.findAllById(questionIds).associateBy { it.id }
        val categoryNameById = categoryRepository.findAllById(questionsById.values.map { it.categoryId }.distinct())
            .associate { it.id to it.name }
        val tagsJsonByQuestionId = loadTagsJson(questionIds)
        val focusSkillsJsonByQuestionId = loadFocusSkillsJson(questionIds)
        val resumeContextSummaryByQuestionId = loadResumeContextSummary(questionIds, resumeVersionId)

        return questionIds.mapIndexedNotNull { index, questionId ->
            val question = questionsById[questionId] ?: return@mapIndexedNotNull null
            InterviewSessionQuestionEntity(
                interviewSessionId = sessionId,
                questionId = question.id,
                parentSessionQuestionId = null,
                promptText = question.title,
                bodyText = question.body,
                questionSourceType = SOURCE_TYPE_CATALOG_SEED,
                orderIndex = index + 1,
                isFollowUp = false,
                depth = 0,
                categoryName = categoryNameById[question.categoryId],
                tagsJson = tagsJsonByQuestionId[question.id],
                focusSkillNamesJson = focusSkillsJsonByQuestionId[question.id],
                resumeContextSummary = resumeContextSummaryByQuestionId[question.id],
                resumeEvidenceJson = null,
                generationRationale = "Selected from the initial interview question pool.",
                generationStatus = GENERATION_STATUS_SEEDED,
                contentLocale = null,
                createdAt = now,
                updatedAt = now,
                skippedAt = null,
            )
        }
    }

    private fun buildInitialRows(
        userId: Long,
        session: InterviewSessionEntity,
        requestedCount: Int,
        seedQuestionIds: List<Long>,
        resumeVersionId: Long?,
        coverageItems: List<InterviewSessionEvidenceItemEntity>,
        now: java.time.Instant,
    ): List<InterviewSessionQuestionEntity> {
        if (session.interviewMode == INTERVIEW_MODE_FULL_COVERAGE && session.sessionType == SESSION_TYPE_RESUME_MOCK && resumeVersionId != null) {
            val openingRow = buildFullCoverageOpeningRow(
                userId = userId,
                session = session,
                resumeVersionId = resumeVersionId,
                coverageItems = coverageItems,
                now = now,
            )
            if (openingRow != null) {
                return listOf(openingRow)
            }
        }
        if (session.sessionType == SESSION_TYPE_RESUME_MOCK && resumeVersionId != null) {
            val openingRow = buildGeneratedOpeningRow(
                userId = userId,
                session = session,
                resumeVersionId = resumeVersionId,
                preferredEvidenceCandidates = emptyList(),
                now = now,
            )
            if (openingRow != null) {
                return listOf(openingRow)
            }
        }

        val questionIds = resolveQuestionIds(
            userId = userId,
            sessionType = session.sessionType,
            requestedCount = requestedCount,
            seedQuestionIds = seedQuestionIds,
        )
        if (questionIds.isEmpty()) {
            return emptyList()
        }
        return buildSeedRows(session.id, questionIds, resumeVersionId, now)
    }

    private fun buildGeneratedOpeningRow(
        userId: Long,
        session: InterviewSessionEntity,
        resumeVersionId: Long,
        preferredEvidenceCandidates: List<InterviewResumeEvidenceCandidate>,
        now: java.time.Instant,
    ): InterviewSessionQuestionEntity? {
        val generated = interviewOpeningGenerationService.generateResumeOpening(
            resumeVersionId = resumeVersionId,
            preferredEvidenceCandidates = preferredEvidenceCandidates,
        ) ?: return null
        val categoryId = resolveGeneratedQuestionCategoryId(userId)
        val generatedQuestion = questionRepository.save(
            QuestionEntity(
                authorUserId = userId,
                categoryId = categoryId,
                title = generated.promptText,
                body = generated.bodyText ?: generated.promptText,
                questionType = QUESTION_TYPE_BEHAVIORAL,
                difficultyLevel = QUESTION_DIFFICULTY_MEDIUM,
                sourceType = QUESTION_SOURCE_TYPE_INTERVIEW_AI,
                qualityStatus = QUESTION_QUALITY_STATUS_GENERATED,
                visibility = QUESTION_VISIBILITY_PRIVATE,
                expectedAnswerSeconds = DEFAULT_EXPECTED_ANSWER_SECONDS,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val categoryName = categoryRepository.findById(categoryId).orElse(null)?.name
        return InterviewSessionQuestionEntity(
            interviewSessionId = session.id,
            questionId = generatedQuestion.id,
            parentSessionQuestionId = null,
            promptText = generated.promptText,
            bodyText = generated.bodyText,
            questionSourceType = SOURCE_TYPE_AI_OPENING,
            orderIndex = 1,
            isFollowUp = false,
            depth = 0,
            categoryName = categoryName,
            tagsJson = objectMapper.writeValueAsString(generated.tags),
            focusSkillNamesJson = objectMapper.writeValueAsString(generated.focusSkillNames),
            resumeContextSummary = generated.resumeContextSummary,
            resumeEvidenceJson = encodeResumeEvidence(generated.resumeEvidence),
            generationRationale = generated.generationRationale,
            generationStatus = GENERATION_STATUS_AI_GENERATED,
            llmModel = generated.llmModel,
            llmPromptVersion = generated.llmPromptVersion,
            contentLocale = generated.contentLocale,
            createdAt = now,
            updatedAt = now,
            skippedAt = null,
        )
    }

    private fun buildFullCoverageOpeningRow(
        userId: Long,
        session: InterviewSessionEntity,
        resumeVersionId: Long,
        coverageItems: List<InterviewSessionEvidenceItemEntity>,
        now: java.time.Instant,
    ): InterviewSessionQuestionEntity? {
        val targetItem = coverageItems.firstOrNull() ?: return null
        val preferredEvidenceCandidate = targetItem.toEvidenceCandidate()
        return buildGeneratedOpeningRow(
            userId = userId,
            session = session,
            resumeVersionId = resumeVersionId,
            preferredEvidenceCandidates = listOf(preferredEvidenceCandidate),
            now = now,
        ) ?: buildDeterministicCoverageRow(
            userId = userId,
            session = session,
            targetItem = targetItem,
            orderIndex = 1,
            parentSessionQuestionId = null,
            isFollowUp = false,
            depth = 0,
            sourceType = SOURCE_TYPE_COVERAGE_PLANNER,
            generationStatus = GENERATION_STATUS_COVERAGE_PLANNED,
            generationRationale = deterministicCoverageRationale(appLocaleService.resolveLanguage()),
            now = now,
        )
    }

    private fun maybeInsertFollowUp(
        userId: Long,
        session: InterviewSessionEntity,
        sessionId: Long,
        answeredRow: InterviewSessionQuestionEntity,
        answerText: String,
        resumeVersionId: Long?,
        now: java.time.Instant,
    ): Boolean {
        if (answeredRow.depth >= maxFollowUpDepth) {
            return false
        }
        val parentQuestionId = answeredRow.questionId ?: return false
        val existingRows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(sessionId)
        if (existingRows.any { it.parentSessionQuestionId == answeredRow.id }) {
            return false
        }

        if (session.sessionType == SESSION_TYPE_RESUME_MOCK) {
            val followUpContext = buildFollowUpResumeEvidenceContext(
                existingRows = existingRows,
                answeredRow = answeredRow,
                resumeVersionId = resumeVersionId,
            )
            val generated = interviewFollowUpGenerationService.generateResumeFollowUp(
                session = session,
                answeredRow = answeredRow,
                answerText = answerText,
                parentTags = decodeJsonArray(answeredRow.tagsJson),
                parentFocusSkillNames = decodeJsonArray(answeredRow.focusSkillNamesJson),
                parentResumeEvidenceCandidates = followUpContext.parentCandidates,
                preferredResumeEvidenceCandidates = followUpContext.preferredCandidates,
                usedFacetsForPreferredRecord = followUpContext.usedFacetsForPreferredRecord,
            )
            if (generated != null) {
                insertGeneratedFollowUp(
                    userId = userId,
                    session = session,
                    parentQuestionId = parentQuestionId,
                    generated = generated,
                    answeredRow = answeredRow,
                    existingRows = existingRows,
                    now = now,
                )
                return true
            }
        }

        val edge = questionRelationshipRepository.findByParentQuestionIdOrderByDisplayOrderAscIdAsc(parentQuestionId)
            .firstOrNull { it.relationshipType.lowercase() == RELATIONSHIP_TYPE_FOLLOW_UP }
            ?: return false
        val childQuestion = questionRepository.findByIdAndIsActiveTrue(edge.childQuestionId) ?: return false
        if (existingRows.any { it.questionId == childQuestion.id && it.parentSessionQuestionId == answeredRow.id }) {
            return false
        }

        shiftRowsForInsertion(existingRows, answeredRow.orderIndex, now)

        val categoryName = categoryRepository.findById(childQuestion.categoryId).orElse(null)?.name
        val tagsJson = loadTagsJson(listOf(childQuestion.id))[childQuestion.id]
        val focusSkillsJson = loadFocusSkillsJson(listOf(childQuestion.id))[childQuestion.id]
        val resumeContextSummary = loadResumeContextSummary(listOf(childQuestion.id), resumeVersionId)[childQuestion.id]
        interviewSessionQuestionRepository.save(
            InterviewSessionQuestionEntity(
                interviewSessionId = sessionId,
                questionId = childQuestion.id,
                parentSessionQuestionId = answeredRow.id,
                promptText = childQuestion.title,
                bodyText = childQuestion.body,
                questionSourceType = SOURCE_TYPE_CATALOG_FOLLOW_UP,
                orderIndex = answeredRow.orderIndex + 1,
                isFollowUp = true,
                depth = answeredRow.depth + 1,
                categoryName = categoryName,
                tagsJson = tagsJson,
                focusSkillNamesJson = focusSkillsJson,
                resumeContextSummary = resumeContextSummary,
                resumeEvidenceJson = null,
                generationRationale = "Follow-up selected from the catalog relationship graph.",
                generationStatus = GENERATION_STATUS_CATALOG_FOLLOW_UP,
                contentLocale = null,
                createdAt = now,
                updatedAt = now,
                skippedAt = null,
            ),
        )
        return true
    }

    private fun insertGeneratedFollowUp(
        userId: Long,
        session: InterviewSessionEntity,
        parentQuestionId: Long,
        generated: GeneratedInterviewFollowUp,
        answeredRow: InterviewSessionQuestionEntity,
        existingRows: List<InterviewSessionQuestionEntity>,
        now: java.time.Instant,
    ) {
        val parentQuestion = questionRepository.findByIdAndIsActiveTrue(parentQuestionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Parent interview question not found: $parentQuestionId")

        shiftRowsForInsertion(existingRows, answeredRow.orderIndex, now)

        val generatedQuestion = questionRepository.save(
            QuestionEntity(
                authorUserId = userId,
                categoryId = parentQuestion.categoryId,
                title = generated.promptText,
                body = generated.bodyText ?: generated.promptText,
                questionType = parentQuestion.questionType,
                difficultyLevel = parentQuestion.difficultyLevel,
                sourceType = QUESTION_SOURCE_TYPE_INTERVIEW_AI,
                qualityStatus = QUESTION_QUALITY_STATUS_GENERATED,
                visibility = QUESTION_VISIBILITY_PRIVATE,
                expectedAnswerSeconds = parentQuestion.expectedAnswerSeconds,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val savedRow = interviewSessionQuestionRepository.save(
            InterviewSessionQuestionEntity(
                interviewSessionId = answeredRow.interviewSessionId,
                questionId = generatedQuestion.id,
                parentSessionQuestionId = answeredRow.id,
                promptText = generated.promptText,
                bodyText = generated.bodyText,
                questionSourceType = SOURCE_TYPE_AI_FOLLOW_UP,
                orderIndex = answeredRow.orderIndex + 1,
                isFollowUp = true,
                depth = answeredRow.depth + 1,
                categoryName = answeredRow.categoryName,
                tagsJson = objectMapper.writeValueAsString(generated.tags),
                focusSkillNamesJson = objectMapper.writeValueAsString(generated.focusSkillNames),
                resumeContextSummary = generated.resumeContextSummary,
                resumeEvidenceJson = encodeResumeEvidence(generated.resumeEvidence),
                generationRationale = generated.generationRationale,
                generationStatus = GENERATION_STATUS_AI_GENERATED,
                llmModel = generated.llmModel,
                llmPromptVersion = generated.llmPromptVersion,
                contentLocale = generated.contentLocale,
                createdAt = now,
                updatedAt = now,
                skippedAt = null,
            ),
        )
        syncCoverageLinks(session, listOf(savedRow), now)
    }

    private fun initializeCoverageInventory(
        session: InterviewSessionEntity,
        resumeVersionId: Long?,
        now: java.time.Instant,
    ): List<InterviewSessionEvidenceItemEntity> {
        if (session.interviewMode != INTERVIEW_MODE_FULL_COVERAGE) {
            return emptyList()
        }
        if (session.sessionType != SESSION_TYPE_RESUME_MOCK || resumeVersionId == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "full_coverage interviewMode is only supported for resume_mock sessions with resumeVersionId",
            )
        }

        val candidates = interviewResumeEvidenceAssembler.loadCandidates(resumeVersionId, FULL_COVERAGE_EVIDENCE_LIMIT)
        if (candidates.isEmpty()) {
            return emptyList()
        }
        return interviewSessionEvidenceItemRepository.saveAll(
            candidates.mapIndexed { index, candidate ->
                InterviewSessionEvidenceItemEntity(
                    interviewSessionId = session.id,
                    section = candidate.section,
                    label = candidate.label,
                    snippet = candidate.snippet,
                    sourceRecordType = candidate.sourceRecordType,
                    sourceRecordId = candidate.sourceRecordId,
                    facet = candidate.facet,
                    coverageStatus = COVERAGE_STATUS_UNASKED,
                    coveragePriority = coveragePriority(candidate.section, index),
                    displayOrder = index + 1,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }

    private fun syncCoverageLinks(
        session: InterviewSessionEntity,
        rows: List<InterviewSessionQuestionEntity>,
        now: java.time.Instant,
    ) {
        if (session.interviewMode != INTERVIEW_MODE_FULL_COVERAGE || rows.isEmpty()) {
            return
        }
        val evidenceItems = interviewSessionEvidenceItemRepository.findByInterviewSessionIdOrderByDisplayOrderAscIdAsc(session.id)
        if (evidenceItems.isEmpty()) {
            return
        }
        val evidenceBySourceKey = evidenceItems.groupBy { evidenceKey(it.sourceRecordType, it.sourceRecordId) }
        val evidenceBySnippet = evidenceItems.associateBy { normalizeSnippet(it.snippet) }
        rows.forEach { row ->
            val resumeEvidence = decodeResumeEvidence(row.resumeEvidenceJson)
            val matchedEvidenceIds = linkedSetOf<Long>()
            resumeEvidence.forEach { evidence ->
                val exactSnippet = normalizeSnippet(evidence.snippet)
                val matchedItem = when {
                    evidence.sourceRecordType != null && evidence.sourceRecordId != null ->
                        evidenceBySourceKey[evidenceKey(evidence.sourceRecordType, evidence.sourceRecordId)]
                            .orEmpty()
                            .firstOrNull { normalizeSnippet(it.snippet) == exactSnippet }
                            ?: evidenceBySourceKey[evidenceKey(evidence.sourceRecordType, evidence.sourceRecordId)].orEmpty().firstOrNull()
                    else -> null
                } ?: evidenceBySnippet[exactSnippet]
                if (matchedItem != null) {
                    matchedEvidenceIds += matchedItem.id
                }
            }
            if (matchedEvidenceIds.isEmpty() && row.parentSessionQuestionId != null) {
                interviewSessionQuestionEvidenceLinkRepository.findByIdInterviewSessionQuestionIdIn(listOf(row.parentSessionQuestionId))
                    .map { it.id.interviewSessionEvidenceItemId }
                    .forEach { matchedEvidenceIds += it }
            }
            if (matchedEvidenceIds.isEmpty()) {
                val firstUnlinkedItem = evidenceItems.firstOrNull { item ->
                    !interviewSessionQuestionEvidenceLinkRepository.existsById(
                        InterviewSessionQuestionEvidenceLinkId(
                            interviewSessionQuestionId = row.id,
                            interviewSessionEvidenceItemId = item.id,
                        ),
                    ) && item.coverageStatus == COVERAGE_STATUS_UNASKED
                }
                if (firstUnlinkedItem != null) {
                    matchedEvidenceIds += firstUnlinkedItem.id
                }
            }
            matchedEvidenceIds.forEachIndexed { index, evidenceItemId ->
                val linkId = InterviewSessionQuestionEvidenceLinkId(
                    interviewSessionQuestionId = row.id,
                    interviewSessionEvidenceItemId = evidenceItemId,
                )
                if (!interviewSessionQuestionEvidenceLinkRepository.existsById(linkId)) {
                    interviewSessionQuestionEvidenceLinkRepository.save(
                        InterviewSessionQuestionEvidenceLinkEntity(
                            id = linkId,
                            linkRole = if (index == 0) LINK_ROLE_PRIMARY else LINK_ROLE_SUPPORTING,
                            createdAt = now,
                        ),
                    )
                }
                markCoverageStatus(evidenceItemId, COVERAGE_STATUS_ASKED, now)
            }
        }
    }

    private fun updateCoverageAfterAnswer(
        session: InterviewSessionEntity,
        answeredRow: InterviewSessionQuestionEntity,
        totalScore: Int,
        now: java.time.Instant,
    ) {
        if (session.interviewMode != INTERVIEW_MODE_FULL_COVERAGE) {
            return
        }
        val links = interviewSessionQuestionEvidenceLinkRepository.findByIdInterviewSessionQuestionIdIn(listOf(answeredRow.id))
        if (links.isEmpty()) {
            return
        }
        val status = if (totalScore >= COVERAGE_DEFENDED_SCORE_THRESHOLD) {
            COVERAGE_STATUS_DEFENDED
        } else {
            COVERAGE_STATUS_WEAK
        }
        links.forEach { link ->
            markCoverageStatus(link.id.interviewSessionEvidenceItemId, status, now)
        }
    }

    private fun maybeInsertNextCoverageQuestion(
        userId: Long,
        session: InterviewSessionEntity,
        now: java.time.Instant,
    ) {
        if (session.interviewMode != INTERVIEW_MODE_FULL_COVERAGE || session.resumeVersionId == null) {
            return
        }
        val nextItem = resolveNextCoverageTarget(session.id) ?: return
        val existingRows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        val nextOrderIndex = (existingRows.maxOfOrNull { it.orderIndex } ?: 0) + 1
        val newRow = buildGeneratedOpeningRow(
            userId = userId,
            session = session,
            resumeVersionId = session.resumeVersionId,
            preferredEvidenceCandidates = listOf(nextItem.toEvidenceCandidate()),
            now = now,
        ) ?: buildDeterministicCoverageRow(
            userId = userId,
            session = session,
            targetItem = nextItem,
            orderIndex = nextOrderIndex,
            parentSessionQuestionId = null,
            isFollowUp = false,
            depth = 0,
            sourceType = SOURCE_TYPE_COVERAGE_PLANNER,
            generationStatus = deterministicCoverageGenerationStatus(nextItem.coverageStatus),
            generationRationale = deterministicCoverageRationale(appLocaleService.resolveLanguage(), nextItem.coverageStatus),
            now = now,
        )
        val savedRow = interviewSessionQuestionRepository.save(newRow.copyForInsert(orderIndex = nextOrderIndex, now = now))
        syncCoverageLinks(session, listOf(savedRow), now)
    }

    private fun resolveNextCoverageTarget(sessionId: Long): InterviewSessionEvidenceItemEntity? {
        val evidenceItems = interviewSessionEvidenceItemRepository.findByInterviewSessionIdOrderByDisplayOrderAscIdAsc(sessionId)
        if (evidenceItems.isEmpty()) {
            return null
        }
        val availableFacetsByRecord = evidenceItems
            .groupBy { evidenceKey(it.sourceRecordType, it.sourceRecordId) }
            .mapValues { (_, items) -> items.map { it.facet }.distinct() }
        val usedFacetsByRecord = evidenceItems
            .filter { it.coverageStatus != COVERAGE_STATUS_UNASKED }
            .groupBy { evidenceKey(it.sourceRecordType, it.sourceRecordId) }
            .mapValues { (_, items) -> items.map { it.facet }.distinct() }
        val linkCountsByEvidenceId = interviewSessionQuestionEvidenceLinkRepository
            .findByIdInterviewSessionEvidenceItemIdIn(evidenceItems.map { it.id })
            .groupingBy { it.id.interviewSessionEvidenceItemId }
            .eachCount()
        val facetLinkCountsByRecord = evidenceItems.associate { item ->
            val sameRecordFacetLinkCount = evidenceItems
                .filter {
                    it.sourceRecordType == item.sourceRecordType &&
                        it.sourceRecordId == item.sourceRecordId &&
                        it.facet == item.facet
                }
                .sumOf { candidate -> linkCountsByEvidenceId[candidate.id] ?: 0 }
            item.id to sameRecordFacetLinkCount
        }
        return evidenceItems
            .sortedWith(
                compareBy<InterviewSessionEvidenceItemEntity> { if (it.coverageStatus == COVERAGE_STATUS_UNASKED) 0 else 1 }
                    .thenBy {
                        facetPathPriority(
                            facet = it.facet,
                            usedFacets = usedFacetsByRecord[evidenceKey(it.sourceRecordType, it.sourceRecordId)].orEmpty(),
                            availableFacets = availableFacetsByRecord[evidenceKey(it.sourceRecordType, it.sourceRecordId)].orEmpty(),
                        )
                    }
                    .thenByDescending { extendedCoveragePriority(it.coverageStatus) }
                    .thenBy { facetLinkCountsByRecord[it.id] ?: 0 }
                    .thenBy { linkCountsByEvidenceId[it.id] ?: 0 }
                    .thenByDescending { it.coveragePriority }
                    .thenBy { it.displayOrder }
                    .thenBy { it.id },
            )
            .firstOrNull()
    }

    private fun buildDeterministicCoverageRow(
        userId: Long,
        session: InterviewSessionEntity,
        targetItem: InterviewSessionEvidenceItemEntity,
        orderIndex: Int,
        parentSessionQuestionId: Long?,
        isFollowUp: Boolean,
        depth: Int,
        sourceType: String,
        generationStatus: String,
        generationRationale: String,
        now: java.time.Instant,
    ): InterviewSessionQuestionEntity {
        val categoryId = resolveGeneratedQuestionCategoryId(userId)
        val contentLocale = appLocaleService.resolveLanguage()
        val title = deterministicCoverageTitle(targetItem, contentLocale)
        val body = deterministicCoverageBody(targetItem, contentLocale)
        val generatedQuestion = questionRepository.save(
            QuestionEntity(
                authorUserId = userId,
                categoryId = categoryId,
                title = title,
                body = body,
                questionType = QUESTION_TYPE_BEHAVIORAL,
                difficultyLevel = QUESTION_DIFFICULTY_MEDIUM,
                sourceType = QUESTION_SOURCE_TYPE_INTERVIEW_AI,
                qualityStatus = QUESTION_QUALITY_STATUS_GENERATED,
                visibility = QUESTION_VISIBILITY_PRIVATE,
                expectedAnswerSeconds = DEFAULT_EXPECTED_ANSWER_SECONDS,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val categoryName = categoryRepository.findById(categoryId).orElse(null)?.name
        return InterviewSessionQuestionEntity(
            interviewSessionId = session.id,
            questionId = generatedQuestion.id,
            parentSessionQuestionId = parentSessionQuestionId,
            promptText = title,
            bodyText = body,
            questionSourceType = sourceType,
            orderIndex = orderIndex,
            isFollowUp = isFollowUp,
            depth = depth,
            categoryName = categoryName,
            tagsJson = objectMapper.writeValueAsString(listOf(targetItem.section, "resume", "coverage")),
            focusSkillNamesJson = objectMapper.writeValueAsString(emptyList<String>()),
            resumeContextSummary = targetItem.snippet,
            resumeEvidenceJson = objectMapper.writeValueAsString(
                listOf(
                    GeneratedInterviewResumeEvidence(
                        section = targetItem.section,
                        label = targetItem.label,
                        snippet = targetItem.snippet,
                        sourceRecordType = targetItem.sourceRecordType,
                        sourceRecordId = targetItem.sourceRecordId,
                        confidence = 1.0,
                    ),
                ),
            ),
            generationRationale = generationRationale,
            generationStatus = generationStatus,
            contentLocale = contentLocale,
            createdAt = now,
            updatedAt = now,
            skippedAt = null,
        )
    }

    private fun loadLinkedQuestionIdsByEvidenceId(evidenceItemIds: List<Long>): Map<Long, List<Long>> {
        if (evidenceItemIds.isEmpty()) {
            return emptyMap()
        }
        return interviewSessionQuestionEvidenceLinkRepository.findByIdInterviewSessionEvidenceItemIdIn(evidenceItemIds)
            .groupBy { it.id.interviewSessionEvidenceItemId }
            .mapValues { (_, links) -> links.map { it.id.interviewSessionQuestionId }.distinct() }
    }

    private fun buildFollowUpResumeEvidenceContext(
        existingRows: List<InterviewSessionQuestionEntity>,
        answeredRow: InterviewSessionQuestionEntity,
        resumeVersionId: Long?,
    ): FollowUpResumeEvidenceContext {
        val allCandidates = buildSessionResumeEvidenceCandidates(
            sessionId = answeredRow.interviewSessionId,
            resumeVersionId = resumeVersionId,
        )
        if (allCandidates.isEmpty()) {
            return FollowUpResumeEvidenceContext()
        }

        val rowCandidatesById = existingRows.associate { row ->
            row.id to matchRowResumeEvidenceCandidates(row, allCandidates)
        }
        val parentCandidates = rowCandidatesById[answeredRow.id].orEmpty()
        if (parentCandidates.isEmpty()) {
            return FollowUpResumeEvidenceContext()
        }

        val preferredRecordKey = evidenceKey(parentCandidates.first().sourceRecordType, parentCandidates.first().sourceRecordId)
        val usedCandidatesForRecord = rowCandidatesById.values
            .flatten()
            .filter { evidenceKey(it.sourceRecordType, it.sourceRecordId) == preferredRecordKey }
        val usedFacetsForPreferredRecord = usedCandidatesForRecord.map { it.facet }.distinct()
        val parentSnippetKeys = parentCandidates.map { normalizeSnippet(it.snippet) }.toSet()
        val usedSnippetCounts = usedCandidatesForRecord.groupingBy { normalizeSnippet(it.snippet) }.eachCount()
        val usedFacetCounts = usedCandidatesForRecord.groupingBy { it.facet }.eachCount()
        val sameRecordCandidates = allCandidates
            .filter { evidenceKey(it.sourceRecordType, it.sourceRecordId) == preferredRecordKey }
            .distinctBy { Triple(it.sourceRecordType, it.sourceRecordId, normalizeSnippet(it.snippet)) }
        val availableFacets = sameRecordCandidates.map { it.facet }.distinct()

        val preferredCandidates = sameRecordCandidates
            .sortedWith(
                compareBy<InterviewResumeEvidenceCandidate>(
                    {
                        facetPathPriority(
                            facet = it.facet,
                            usedFacets = usedFacetsForPreferredRecord,
                            availableFacets = availableFacets,
                        )
                    },
                    { if (it.facet in usedFacetsForPreferredRecord) 1 else 0 },
                    { if (normalizeSnippet(it.snippet) in parentSnippetKeys) 1 else 0 },
                    { usedFacetCounts[it.facet] ?: 0 },
                    { usedSnippetCounts[normalizeSnippet(it.snippet)] ?: 0 },
                ).thenBy { it.facet }.thenBy { it.label ?: "" }.thenBy { it.snippet },
            )
            .take(MAX_PREFERRED_FOLLOW_UP_EVIDENCE_CANDIDATES)

        return FollowUpResumeEvidenceContext(
            parentCandidates = parentCandidates,
            preferredCandidates = preferredCandidates,
            usedFacetsForPreferredRecord = usedFacetsForPreferredRecord,
        )
    }

    private fun buildSessionResumeEvidenceCandidates(
        sessionId: Long,
        resumeVersionId: Long?,
    ): List<InterviewResumeEvidenceCandidate> {
        val sessionCandidates = interviewSessionEvidenceItemRepository
            .findByInterviewSessionIdOrderByDisplayOrderAscIdAsc(sessionId)
            .map { it.toEvidenceCandidate() }
            .distinctBy { Triple(it.sourceRecordType, it.sourceRecordId, normalizeSnippet(it.snippet)) }
        if (sessionCandidates.isNotEmpty()) {
            return sessionCandidates
        }
        if (resumeVersionId == null) {
            return emptyList()
        }
        return interviewResumeEvidenceAssembler
            .loadCandidates(resumeVersionId, FULL_COVERAGE_EVIDENCE_LIMIT)
            .distinctBy { Triple(it.sourceRecordType, it.sourceRecordId, normalizeSnippet(it.snippet)) }
    }

    private fun matchRowResumeEvidenceCandidates(
        row: InterviewSessionQuestionEntity,
        allCandidates: List<InterviewResumeEvidenceCandidate>,
    ): List<InterviewResumeEvidenceCandidate> {
        val candidatesBySourceKey = allCandidates.groupBy { evidenceKey(it.sourceRecordType, it.sourceRecordId) }
        val candidatesBySnippet = allCandidates.associateBy { normalizeSnippet(it.snippet) }
        return decodeResumeEvidence(row.resumeEvidenceJson)
            .mapNotNull { evidence ->
                val exactSnippet = normalizeSnippet(evidence.snippet)
                when {
                    evidence.sourceRecordType != null && evidence.sourceRecordId != null ->
                        candidatesBySourceKey[evidenceKey(evidence.sourceRecordType, evidence.sourceRecordId)]
                            .orEmpty()
                            .firstOrNull { normalizeSnippet(it.snippet) == exactSnippet }
                            ?: candidatesBySourceKey[evidenceKey(evidence.sourceRecordType, evidence.sourceRecordId)].orEmpty().firstOrNull()

                    else -> null
                } ?: candidatesBySnippet[exactSnippet]
            }
            .distinctBy { Triple(it.sourceRecordType, it.sourceRecordId, normalizeSnippet(it.snippet)) }
    }

    private fun markCoverageStatus(evidenceItemId: Long, nextStatus: String, now: java.time.Instant) {
        val current = interviewSessionEvidenceItemRepository.findById(evidenceItemId).orElse(null) ?: return
        if (current.coverageStatus == nextStatus) {
            return
        }
        interviewSessionEvidenceItemRepository.save(
            InterviewSessionEvidenceItemEntity(
                id = current.id,
                interviewSessionId = current.interviewSessionId,
                section = current.section,
                label = current.label,
                snippet = current.snippet,
                facet = current.facet,
                sourceRecordType = current.sourceRecordType,
                sourceRecordId = current.sourceRecordId,
                coverageStatus = nextStatus,
                coveragePriority = current.coveragePriority,
                displayOrder = current.displayOrder,
                createdAt = current.createdAt,
                updatedAt = now,
            ),
        )
    }

    private fun InterviewSessionEvidenceItemEntity.toEvidenceCandidate(): InterviewResumeEvidenceCandidate =
        InterviewResumeEvidenceCandidate(
            section = section,
            label = label,
            snippet = snippet,
            facet = facet,
            sourceRecordType = sourceRecordType,
            sourceRecordId = sourceRecordId,
        )

    private fun InterviewSessionQuestionEntity.copyForInsert(orderIndex: Int = this.orderIndex, now: java.time.Instant): InterviewSessionQuestionEntity =
        InterviewSessionQuestionEntity(
            id = id,
            interviewSessionId = interviewSessionId,
            questionId = questionId,
            parentSessionQuestionId = parentSessionQuestionId,
            promptText = promptText,
            bodyText = bodyText,
            questionSourceType = questionSourceType,
            orderIndex = orderIndex,
            isFollowUp = isFollowUp,
            depth = depth,
            categoryName = categoryName,
            tagsJson = tagsJson,
            focusSkillNamesJson = focusSkillNamesJson,
            resumeContextSummary = resumeContextSummary,
            resumeEvidenceJson = resumeEvidenceJson,
            generationRationale = generationRationale,
            generationStatus = generationStatus,
            llmModel = llmModel,
            llmPromptVersion = llmPromptVersion,
            contentLocale = contentLocale,
            answerAttemptId = answerAttemptId,
            skippedAt = skippedAt,
            createdAt = createdAt,
            updatedAt = now,
        )

    private fun coveragePriority(section: String, index: Int): Int = when (section) {
        "project" -> 500 - index
        "experience" -> 400 - index
        "award" -> 300 - index
        "certification" -> 200 - index
        "education" -> 100 - index
        else -> 50 - index
    }

    private fun deterministicCoverageTitle(targetItem: InterviewSessionEvidenceItemEntity, language: String): String =
        when (language.lowercase()) {
            "en" -> deterministicCoverageTitleEn(targetItem)
            else -> deterministicCoverageTitleKo(targetItem)
        }

    private fun deterministicCoverageBody(targetItem: InterviewSessionEvidenceItemEntity, language: String): String =
        when (language.lowercase()) {
            "en" -> "Resume evidence: ${targetItem.snippet}\n${deterministicCoverageBodyEn(targetItem)}"
            else -> "이력서 근거: ${targetItem.snippet}\n${deterministicCoverageBodyKo(targetItem)}"
        }

    private fun deterministicCoverageTitleEn(targetItem: InterviewSessionEvidenceItemEntity): String = when (targetItem.facet) {
        "problem" -> "What concrete problem or constraint led you to take on ${targetItem.label ?: "this work"}?"
        "action" -> "Walk me through the key implementation decisions you made in ${targetItem.label ?: "this work"}."
        "result" -> "What outcome did ${targetItem.label ?: "this work"} actually produce, and how did you validate it?"
        "metric" -> "Which metrics proved that ${targetItem.label ?: "this work"} was working as intended?"
        "tradeoff" -> "What trade-offs did you weigh while driving ${targetItem.label ?: "this work"}?"
        else -> when (targetItem.section) {
            "project" -> "Walk me through ${targetItem.label ?: "this project"} in concrete detail."
            "experience" -> "Describe the problem and solution behind ${targetItem.label ?: "this experience"}."
            else -> "Explain the concrete example behind ${targetItem.label ?: "this resume detail"}."
        }
    }

    private fun deterministicCoverageTitleKo(targetItem: InterviewSessionEvidenceItemEntity): String = when (targetItem.facet) {
        "problem" -> "${targetItem.label ?: "이 경험"}을 시작하게 된 구체적인 문제와 제약이 무엇이었는지 설명해 주세요."
        "action" -> "${targetItem.label ?: "이 경험"}에서 어떤 구현과 의사결정을 직접 했는지 구체적으로 설명해 주세요."
        "result" -> "${targetItem.label ?: "이 경험"}이 실제로 어떤 결과를 냈고, 그 결과를 어떻게 검증했는지 설명해 주세요."
        "metric" -> "${targetItem.label ?: "이 경험"}의 효과를 어떤 지표로 판단했고 기준을 어떻게 잡았는지 설명해 주세요."
        "tradeoff" -> "${targetItem.label ?: "이 경험"}을 진행하면서 어떤 대안과 트레이드오프를 비교했는지 설명해 주세요."
        else -> when (targetItem.section) {
            "project" -> "${targetItem.label ?: "이 프로젝트"}를 어떤 문제와 맥락에서 진행했는지 구체적으로 설명해 주세요."
            "experience" -> "${targetItem.label ?: "이 경험"}에서 해결하려던 문제와 실제 해결 과정을 설명해 주세요."
            else -> "${targetItem.label ?: "이 이력서 내용"}에 담긴 구체적인 사례를 설명해 주세요."
        }
    }

    private fun deterministicCoverageBodyEn(targetItem: InterviewSessionEvidenceItemEntity): String = when (targetItem.facet) {
        "problem" -> "Focus on the original context, why it mattered, the constraints you had to respect, and what made it difficult."
        "action" -> "Focus on your role, the implementation steps, key decisions, and why you chose that approach over other options."
        "result" -> "Focus on the measurable outcome, how you verified the result, what changed after launch, and what you learned."
        "metric" -> "Focus on the metric definition, baseline, target, instrumentation, and how you knew the numbers were trustworthy."
        "tradeoff" -> "Focus on the alternatives you considered, the decision criteria, the downside you accepted, and what you would change now."
        else -> "Answer with the situation, your role, the decisions you made, the result, and what you learned."
    }

    private fun deterministicCoverageBodyKo(targetItem: InterviewSessionEvidenceItemEntity): String = when (targetItem.facet) {
        "problem" -> "당시 상황, 왜 중요한 문제였는지, 어떤 제약이 있었는지, 왜 어려웠는지까지 설명해 주세요."
        "action" -> "맡았던 역할, 실제 구현 단계, 핵심 의사결정, 다른 선택지 대신 그 방법을 택한 이유까지 설명해 주세요."
        "result" -> "실제 결과가 무엇이었는지, 어떤 방식으로 검증했는지, 배포 이후 무엇이 달라졌는지, 배운 점까지 설명해 주세요."
        "metric" -> "어떤 지표를 봤는지, 기준선과 목표를 어떻게 잡았는지, 측정 방식이 왜 신뢰할 수 있었는지까지 설명해 주세요."
        "tradeoff" -> "비교한 대안, 판단 기준, 감수한 단점, 지금 다시 한다면 무엇을 바꿀지까지 설명해 주세요."
        else -> "상황, 맡았던 역할, 내린 의사결정, 결과, 그리고 배운 점까지 포함해 설명해 주세요."
    }

    private fun deterministicCoverageRationale(language: String): String =
        deterministicCoverageRationale(language, COVERAGE_STATUS_UNASKED)

    private fun deterministicCoverageRationale(language: String, targetCoverageStatus: String): String =
        if (targetCoverageStatus == COVERAGE_STATUS_UNASKED) {
            if (language.lowercase() == "en") {
                "Generated from the next uncovered resume evidence item in full coverage mode."
            } else {
                "풀 커버리지 모드에서 아직 질문하지 않은 다음 이력서 근거를 바탕으로 생성했습니다."
            }
        } else {
            if (language.lowercase() == "en") {
                "Generated as an additional deep-dive question after full coverage was reached."
            } else {
                "풀 커버리지 100% 이후에도 심화 검증을 위해 추가 생성한 질문입니다."
            }
        }

    private fun deterministicCoverageGenerationStatus(targetCoverageStatus: String): String =
        if (targetCoverageStatus == COVERAGE_STATUS_UNASKED) {
            GENERATION_STATUS_COVERAGE_PLANNED
        } else {
            GENERATION_STATUS_COVERAGE_EXTENDED
        }

    private fun facetPathPriority(
        facet: String,
        usedFacets: List<String>,
        availableFacets: List<String>,
    ): Int {
        val normalizedFacet = facet.ifBlank { "general" }
        val unresolvedProgression = FACET_PROGRESSION.filter { candidate ->
            candidate in availableFacets && candidate !in usedFacets
        }
        if (unresolvedProgression.isNotEmpty()) {
            val unresolvedIndex = unresolvedProgression.indexOf(normalizedFacet)
            if (unresolvedIndex >= 0) {
                return unresolvedIndex
            }
        }
        val remainingAvailable = availableFacets.distinct()
        val progressionIndex = FACET_PROGRESSION.indexOf(normalizedFacet).takeIf { it >= 0 } ?: FACET_PROGRESSION.size
        return when {
            normalizedFacet !in remainingAvailable -> 200 + progressionIndex
            normalizedFacet !in usedFacets -> 100 + progressionIndex
            else -> 300 + progressionIndex
        }
    }

    private fun extendedCoveragePriority(status: String): Int = when (status) {
        COVERAGE_STATUS_WEAK -> 4
        COVERAGE_STATUS_SKIPPED -> 3
        COVERAGE_STATUS_ASKED -> 2
        COVERAGE_STATUS_DEFENDED -> 1
        else -> 0
    }

    private fun evidenceKey(sourceRecordType: String, sourceRecordId: Long): String = "$sourceRecordType:$sourceRecordId"

    private fun normalizeSnippet(value: String): String = value.replace(Regex("\\s+"), " ").trim().lowercase()

    private fun percent(value: Int, total: Int): Int =
        if (total <= 0) 0 else ((value.toDouble() / total.toDouble()) * 100.0).toInt()

    private fun shiftRowsForInsertion(
        existingRows: List<InterviewSessionQuestionEntity>,
        afterOrderIndex: Int,
        now: java.time.Instant,
    ) {
        existingRows.asSequence()
            .filter { it.orderIndex > afterOrderIndex }
            .sortedByDescending { it.orderIndex }
            .forEach { row ->
                interviewSessionQuestionRepository.saveAndFlush(
                    InterviewSessionQuestionEntity(
                        id = row.id,
                        interviewSessionId = row.interviewSessionId,
                        questionId = row.questionId,
                        parentSessionQuestionId = row.parentSessionQuestionId,
                        promptText = row.promptText,
                        bodyText = row.bodyText,
                        questionSourceType = row.questionSourceType,
                        orderIndex = row.orderIndex + 1,
                        isFollowUp = row.isFollowUp,
                        depth = row.depth,
                        categoryName = row.categoryName,
                        tagsJson = row.tagsJson,
                        focusSkillNamesJson = row.focusSkillNamesJson,
                        resumeContextSummary = row.resumeContextSummary,
                        resumeEvidenceJson = row.resumeEvidenceJson,
                        generationRationale = row.generationRationale,
                        generationStatus = row.generationStatus,
                        llmModel = row.llmModel,
                        llmPromptVersion = row.llmPromptVersion,
                        contentLocale = row.contentLocale,
                        answerAttemptId = row.answerAttemptId,
                        skippedAt = row.skippedAt,
                        createdAt = row.createdAt,
                        updatedAt = now,
                    ),
                )
            }
    }

    private fun loadTagsJson(questionIds: List<Long>): Map<Long, String> {
        if (questionIds.isEmpty()) {
            return emptyMap()
        }
        val edges = questionTagRepository.findByIdQuestionIdIn(questionIds)
        if (edges.isEmpty()) {
            return emptyMap()
        }
        val tagsById = tagRepository.findAllById(edges.map { it.id.tagId }.distinct()).associateBy { it.id }
        return edges.groupBy { it.id.questionId }.mapValues { (_, rows) ->
            objectMapper.writeValueAsString(
                rows.mapNotNull { edge -> tagsById[edge.id.tagId]?.name }.distinct(),
            )
        }
    }

    private fun loadFocusSkillsJson(questionIds: List<Long>): Map<Long, String> {
        if (questionIds.isEmpty()) {
            return emptyMap()
        }
        val mappings = questionSkillMappingRepository.findByQuestionIdIn(questionIds)
        if (mappings.isEmpty()) {
            return emptyMap()
        }
        val skillsById = skillRepository.findAllById(mappings.map { it.skillId }.distinct()).associateBy { it.id }
        return mappings.groupBy { it.questionId }.mapValues { (_, rows) ->
            objectMapper.writeValueAsString(
                rows.sortedByDescending { it.weight }
                    .mapNotNull { mapping -> skillsById[mapping.skillId]?.name }
                    .distinct(),
            )
        }
    }

    private fun loadResumeContextSummary(
        questionIds: List<Long>,
        resumeVersionId: Long?,
    ): Map<Long, String> {
        if (questionIds.isEmpty() || resumeVersionId == null) {
            return emptyMap()
        }
        val riskByQuestionId = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(resumeVersionId)
            .mapNotNull { risk -> risk.linkedQuestionId?.let { linkedQuestionId -> linkedQuestionId to "${risk.title}: ${risk.description}" } }
            .toMap()
        return questionIds.mapNotNull { questionId ->
            riskByQuestionId[questionId]?.let { questionId to it }
        }.toMap()
    }

    private fun decodeJsonArray(value: String?): List<String> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            objectMapper.readValue(value, object : TypeReference<List<String>>() {})
        }.getOrElse { emptyList() }
    }

    private fun decodeResumeEvidence(value: String?): List<InterviewResumeEvidenceDto> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            objectMapper.readValue(value, object : TypeReference<List<InterviewResumeEvidenceDto>>() {})
        }.getOrElse { emptyList() }
    }

    private fun encodeResumeEvidence(value: List<GeneratedInterviewResumeEvidence>): String? {
        if (value.isEmpty()) {
            return null
        }
        return objectMapper.writeValueAsString(value)
    }

    private fun questionStatus(
        row: InterviewSessionQuestionEntity,
        currentRowId: Long?,
        sessionStatus: String,
    ): String = when {
        row.answerAttemptId != null -> STATUS_ANSWERED
        row.skippedAt != null -> STATUS_SKIPPED
        currentRowId == row.id && sessionStatus == STATUS_IN_PROGRESS -> STATUS_CURRENT
        else -> STATUS_QUEUED
    }

    private fun requireSession(userId: Long, sessionId: Long): InterviewSessionEntity =
        interviewSessionRepository.findByIdAndUserId(sessionId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session not found: $sessionId")

    private fun normalizeSessionType(value: String): String {
        val normalized = value.trim().lowercase()
        if (normalized !in SUPPORTED_SESSION_TYPES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sessionType: $value")
        }
        return normalized
    }

    private fun normalizeInterviewMode(value: String?): String {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: INTERVIEW_MODE_FREE_INTERVIEW
        if (normalized !in SUPPORTED_INTERVIEW_MODES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported interviewMode: $value")
        }
        return normalized
    }

    private fun resolveResumeVersionId(userId: Long, requestedResumeVersionId: Long?): Long? {
        if (requestedResumeVersionId == null) {
            return null
        }
        val version = resumeVersionRepository.findById(requestedResumeVersionId)
            .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resumeVersionId: $requestedResumeVersionId") }
        resumeRepository.findByIdAndUserId(version.resumeId, userId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resumeVersionId: $requestedResumeVersionId")
        return version.id
    }

    private fun resolveRequiredResumeVersionId(userId: Long, sessionType: String, requestedResumeVersionId: Long?): Long? {
        if (sessionType == SESSION_TYPE_RESUME_MOCK && requestedResumeVersionId == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "resumeVersionId is required for resume_mock")
        }
        return resolveResumeVersionId(userId, requestedResumeVersionId)
    }

    private fun resolveResumeVersionId(
        userId: Long,
        requestedResumeVersionId: Long?,
        sessionType: String,
    ): Long? = resolveRequiredResumeVersionId(userId, sessionType, requestedResumeVersionId)

    private fun resolveQuestionIds(
        userId: Long,
        sessionType: String,
        requestedCount: Int,
        seedQuestionIds: List<Long>,
    ): List<Long> {
        val limit = requestedCount.coerceIn(1, 10)
        val selected = linkedSetOf<Long>()
        val activeQuestionsById = questionRepository.findByIsActiveTrue().associateBy { it.id }

        seedQuestionIds.forEach { questionId ->
            if (questionId in activeQuestionsById) {
                selected += questionId
            }
        }

        when (sessionType) {
            SESSION_TYPE_RESUME_MOCK -> {
                questionService.getResumeBasedQuestions(userId, limit * 2).forEach { selected += it.questionId }
            }
            SESSION_TYPE_REVIEW_MOCK -> {
                reviewQueueRepository.findByUserIdAndStatus(userId, REVIEW_STATUS_PENDING)
                    .sortedWith(
                        compareByDescending<com.example.interviewplatform.review.entity.ReviewQueueEntity> { it.priority }
                            .thenBy { it.scheduledFor },
                    )
                    .forEach { selected += it.questionId }
            }
            SESSION_TYPE_TOPIC_MOCK -> Unit
        }

        activeQuestionsById.values.sortedBy { it.id }.forEach { question ->
            if (selected.size >= limit) {
                return@forEach
            }
            selected += question.id
        }

        return selected.take(limit)
    }

    private fun resolveGeneratedQuestionCategoryId(userId: Long): Long {
        val recommendedQuestionId = questionService.getResumeBasedQuestions(userId, 1).firstOrNull()?.questionId
        if (recommendedQuestionId != null) {
            val recommendedQuestion = questionRepository.findByIdAndIsActiveTrue(recommendedQuestionId)
            if (recommendedQuestion != null) {
                return recommendedQuestion.categoryId
            }
        }
        return categoryRepository.findAll().firstOrNull()?.id
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No category available for interview question generation")
    }

    private data class FollowUpResumeEvidenceContext(
        val parentCandidates: List<InterviewResumeEvidenceCandidate> = emptyList(),
        val preferredCandidates: List<InterviewResumeEvidenceCandidate> = emptyList(),
        val usedFacetsForPreferredRecord: List<String> = emptyList(),
    )

    private companion object {
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CURRENT = "current"
        const val STATUS_ANSWERED = "answered"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_QUEUED = "queued"
        const val SOURCE_TYPE_INTERVIEW = "interview"
        const val SOURCE_LABEL_INTERVIEW = "Interview"
        const val SOURCE_TYPE_CATALOG_SEED = "catalog_seed"
        const val SOURCE_TYPE_CATALOG_FOLLOW_UP = "catalog_follow_up"
        const val SOURCE_TYPE_AI_OPENING = "ai_opening"
        const val SOURCE_TYPE_AI_FOLLOW_UP = "ai_follow_up"
        const val SOURCE_TYPE_COVERAGE_PLANNER = "coverage_planner"
        const val GENERATION_STATUS_SEEDED = "seeded"
        const val GENERATION_STATUS_CATALOG_FOLLOW_UP = "catalog_follow_up"
        const val GENERATION_STATUS_AI_GENERATED = "ai_generated"
        const val GENERATION_STATUS_COVERAGE_PLANNED = "coverage_planned"
        const val GENERATION_STATUS_COVERAGE_EXTENDED = "coverage_extended"
        const val SESSION_TYPE_RESUME_MOCK = "resume_mock"
        const val SESSION_TYPE_REVIEW_MOCK = "review_mock"
        const val SESSION_TYPE_TOPIC_MOCK = "topic_mock"
        const val INTERVIEW_MODE_QUICK_SCREEN = "quick_screen"
        const val INTERVIEW_MODE_MOCK_30 = "mock_30"
        const val INTERVIEW_MODE_MOCK_60 = "mock_60"
        const val INTERVIEW_MODE_FREE_INTERVIEW = "free_interview"
        const val INTERVIEW_MODE_FULL_COVERAGE = "full_coverage"
        const val RELATIONSHIP_TYPE_FOLLOW_UP = "follow_up"
        const val REVIEW_STATUS_PENDING = "pending"
        const val QUESTION_SOURCE_TYPE_INTERVIEW_AI = "interview_ai"
        const val QUESTION_QUALITY_STATUS_GENERATED = "generated"
        const val QUESTION_VISIBILITY_PRIVATE = "private"
        const val QUESTION_TYPE_BEHAVIORAL = "behavioral"
        const val QUESTION_DIFFICULTY_MEDIUM = "MEDIUM"
        const val DEFAULT_EXPECTED_ANSWER_SECONDS = 180
        const val LINK_ROLE_PRIMARY = "primary"
        const val LINK_ROLE_SUPPORTING = "supporting"
        const val COVERAGE_STATUS_UNASKED = "unasked"
        const val COVERAGE_STATUS_ASKED = "asked"
        const val COVERAGE_STATUS_DEFENDED = "defended"
        const val COVERAGE_STATUS_WEAK = "weak"
        const val COVERAGE_STATUS_SKIPPED = "skipped"
        const val COVERAGE_DEFENDED_SCORE_THRESHOLD = 70
        const val MAX_PREFERRED_FOLLOW_UP_EVIDENCE_CANDIDATES = 4
        const val FULL_COVERAGE_EVIDENCE_LIMIT = 64
        val FACET_PROGRESSION = listOf("problem", "action", "result", "metric", "tradeoff", "general")
        val SUPPORTED_SESSION_TYPES = setOf(SESSION_TYPE_RESUME_MOCK, SESSION_TYPE_REVIEW_MOCK, SESSION_TYPE_TOPIC_MOCK)
        val SUPPORTED_INTERVIEW_MODES = setOf(
            INTERVIEW_MODE_QUICK_SCREEN,
            INTERVIEW_MODE_MOCK_30,
            INTERVIEW_MODE_MOCK_60,
            INTERVIEW_MODE_FREE_INTERVIEW,
            INTERVIEW_MODE_FULL_COVERAGE,
        )
    }
}
