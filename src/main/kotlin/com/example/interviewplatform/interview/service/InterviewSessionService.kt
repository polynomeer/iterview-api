package com.example.interviewplatform.interview.service

import com.example.interviewplatform.answer.dto.SubmitAnswerRequest
import com.example.interviewplatform.answer.repository.AnswerScoreRepository
import com.example.interviewplatform.answer.service.AnswerProgressSource
import com.example.interviewplatform.answer.service.AnswerService
import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.interview.dto.CreateInterviewSessionRequest
import com.example.interviewplatform.interview.dto.InterviewSessionAdvanceResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionAnswerResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionDetailResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionListItemDto
import com.example.interviewplatform.interview.dto.InterviewResumeEvidenceDto
import com.example.interviewplatform.interview.dto.InterviewSessionQuestionDto
import com.example.interviewplatform.interview.dto.SubmitInterviewSessionAnswerRequest
import com.example.interviewplatform.interview.entity.InterviewSessionEntity
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.interview.mapper.InterviewSessionMapper
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
    private val interviewOpeningGenerationService: InterviewOpeningGenerationService,
    private val interviewFollowUpGenerationService: InterviewFollowUpGenerationService,
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
        val resumeVersionId = resolveResumeVersionId(userId, request.resumeVersionId, sessionType)

        val session = interviewSessionRepository.save(
            InterviewSessionEntity(
                userId = userId,
                resumeVersionId = resumeVersionId,
                sessionType = sessionType,
                status = STATUS_IN_PROGRESS,
                startedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val initialRows = buildInitialRows(
            userId = userId,
            session = session,
            requestedCount = request.questionCount,
            seedQuestionIds = request.seedQuestionIds,
            resumeVersionId = resumeVersionId,
            now = now,
        )
        if (initialRows.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No questions available for interview session")
        }
        interviewSessionQuestionRepository.saveAll(initialRows)
        return getSession(userId, session.id)
    }

    @Transactional(readOnly = true)
    fun getSession(userId: Long, sessionId: Long): InterviewSessionDetailResponseDto {
        val session = requireSession(userId, sessionId)
        val rows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        return toDetailResponse(session, rows)
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
                answerAttemptId = submission.answerAttemptId,
                createdAt = row.createdAt,
                updatedAt = now,
            ),
        )

        maybeInsertFollowUp(
            userId = userId,
            session = session,
            sessionId = session.id,
            answeredRow = row,
            answerText = request.contentText,
            resumeVersionId = session.resumeVersionId,
            now = now,
        )
        val refreshedRows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        val unresolvedNext = refreshedRows.firstOrNull { it.answerAttemptId == null }
        val updatedSession = if (unresolvedNext == null) {
            interviewSessionRepository.save(
                InterviewSessionEntity(
                    id = session.id,
                    userId = session.userId,
                    resumeVersionId = session.resumeVersionId,
                    sessionType = session.sessionType,
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
    fun nextQuestion(userId: Long, sessionId: Long): InterviewSessionAdvanceResponseDto {
        val session = requireSession(userId, sessionId)
        val rows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(session.id)
        val nextRow = rows.firstOrNull { it.answerAttemptId == null }
        val effectiveSession = if (nextRow == null && session.status != STATUS_COMPLETED) {
            val now = clockService.now()
            interviewSessionRepository.save(
                InterviewSessionEntity(
                    id = session.id,
                    userId = session.userId,
                    resumeVersionId = session.resumeVersionId,
                    sessionType = session.sessionType,
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
        val currentRowId = rows.firstOrNull { it.answerAttemptId == null }?.id
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
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private fun buildInitialRows(
        userId: Long,
        session: InterviewSessionEntity,
        requestedCount: Int,
        seedQuestionIds: List<Long>,
        resumeVersionId: Long?,
        now: java.time.Instant,
    ): List<InterviewSessionQuestionEntity> {
        if (session.sessionType == SESSION_TYPE_RESUME_MOCK && resumeVersionId != null) {
            val openingRow = buildGeneratedOpeningRow(
                userId = userId,
                session = session,
                resumeVersionId = resumeVersionId,
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
        now: java.time.Instant,
    ): InterviewSessionQuestionEntity? {
        val generated = interviewOpeningGenerationService.generateResumeOpening(resumeVersionId) ?: return null
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
            createdAt = now,
            updatedAt = now,
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
    ) {
        if (answeredRow.depth >= maxFollowUpDepth) {
            return
        }
        val parentQuestionId = answeredRow.questionId ?: return
        val existingRows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(sessionId)
        if (existingRows.any { it.parentSessionQuestionId == answeredRow.id }) {
            return
        }

        if (session.sessionType == SESSION_TYPE_RESUME_MOCK) {
            val generated = interviewFollowUpGenerationService.generateResumeFollowUp(
                session = session,
                answeredRow = answeredRow,
                answerText = answerText,
                parentTags = decodeJsonArray(answeredRow.tagsJson),
                parentFocusSkillNames = decodeJsonArray(answeredRow.focusSkillNamesJson),
            )
            if (generated != null) {
                insertGeneratedFollowUp(
                    userId = userId,
                    parentQuestionId = parentQuestionId,
                    generated = generated,
                    answeredRow = answeredRow,
                    existingRows = existingRows,
                    now = now,
                )
                return
            }
        }

        val edge = questionRelationshipRepository.findByParentQuestionIdOrderByDisplayOrderAscIdAsc(parentQuestionId)
            .firstOrNull { it.relationshipType.lowercase() == RELATIONSHIP_TYPE_FOLLOW_UP }
            ?: return
        val childQuestion = questionRepository.findByIdAndIsActiveTrue(edge.childQuestionId) ?: return
        if (existingRows.any { it.questionId == childQuestion.id && it.parentSessionQuestionId == answeredRow.id }) {
            return
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
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun insertGeneratedFollowUp(
        userId: Long,
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
        interviewSessionQuestionRepository.save(
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
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

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
                        answerAttemptId = row.answerAttemptId,
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

    private companion object {
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CURRENT = "current"
        const val STATUS_ANSWERED = "answered"
        const val STATUS_QUEUED = "queued"
        const val SOURCE_TYPE_INTERVIEW = "interview"
        const val SOURCE_LABEL_INTERVIEW = "Interview"
        const val SOURCE_TYPE_CATALOG_SEED = "catalog_seed"
        const val SOURCE_TYPE_CATALOG_FOLLOW_UP = "catalog_follow_up"
        const val SOURCE_TYPE_AI_OPENING = "ai_opening"
        const val SOURCE_TYPE_AI_FOLLOW_UP = "ai_follow_up"
        const val GENERATION_STATUS_SEEDED = "seeded"
        const val GENERATION_STATUS_CATALOG_FOLLOW_UP = "catalog_follow_up"
        const val GENERATION_STATUS_AI_GENERATED = "ai_generated"
        const val SESSION_TYPE_RESUME_MOCK = "resume_mock"
        const val SESSION_TYPE_REVIEW_MOCK = "review_mock"
        const val SESSION_TYPE_TOPIC_MOCK = "topic_mock"
        const val RELATIONSHIP_TYPE_FOLLOW_UP = "follow_up"
        const val REVIEW_STATUS_PENDING = "pending"
        const val QUESTION_SOURCE_TYPE_INTERVIEW_AI = "interview_ai"
        const val QUESTION_QUALITY_STATUS_GENERATED = "generated"
        const val QUESTION_VISIBILITY_PRIVATE = "private"
        const val QUESTION_TYPE_BEHAVIORAL = "behavioral"
        const val QUESTION_DIFFICULTY_MEDIUM = "MEDIUM"
        const val DEFAULT_EXPECTED_ANSWER_SECONDS = 180
        val SUPPORTED_SESSION_TYPES = setOf(SESSION_TYPE_RESUME_MOCK, SESSION_TYPE_REVIEW_MOCK, SESSION_TYPE_TOPIC_MOCK)
    }
}
