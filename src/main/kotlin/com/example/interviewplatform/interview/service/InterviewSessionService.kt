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
import com.example.interviewplatform.question.repository.QuestionTagRepository
import com.example.interviewplatform.question.repository.TagRepository
import com.example.interviewplatform.question.service.QuestionService
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val questionTagRepository: QuestionTagRepository,
    private val tagRepository: TagRepository,
    private val categoryRepository: CategoryRepository,
    private val questionService: QuestionService,
    private val answerService: AnswerService,
    private val answerScoreRepository: AnswerScoreRepository,
    private val reviewQueueRepository: ReviewQueueRepository,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val clockService: ClockService,
    private val objectMapper: ObjectMapper,
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
        val resumeVersionId = resolveResumeVersionId(userId, request.resumeVersionId)
        val questionIds = resolveQuestionIds(
            userId = userId,
            sessionType = sessionType,
            requestedCount = request.questionCount,
            seedQuestionIds = request.seedQuestionIds,
        )
        if (questionIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No questions available for interview session")
        }

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
        interviewSessionQuestionRepository.saveAll(
            buildSeedRows(session.id, questionIds, now),
        )
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
                questionSourceType = row.questionSourceType,
                orderIndex = row.orderIndex,
                isFollowUp = row.isFollowUp,
                depth = row.depth,
                categoryName = row.categoryName,
                tagsJson = row.tagsJson,
                answerAttemptId = submission.answerAttemptId,
                createdAt = row.createdAt,
                updatedAt = now,
            ),
        )

        maybeInsertFollowUp(sessionId = session.id, answeredRow = row, now = now)
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
        val currentRowId = rows.firstOrNull { it.answerAttemptId == null }?.id
        val questionDtos = rows.map { row ->
            val question = row.questionId?.let(questionById::get)
            InterviewSessionMapper.toQuestionDto(
                row = row,
                question = question,
                status = questionStatus(row, currentRowId, session.status),
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
        now: java.time.Instant,
    ): List<InterviewSessionQuestionEntity> {
        val questionsById = questionRepository.findAllById(questionIds).associateBy { it.id }
        val categoryNameById = categoryRepository.findAllById(questionsById.values.map { it.categoryId }.distinct())
            .associate { it.id to it.name }
        val tagsJsonByQuestionId = loadTagsJson(questionIds)

        return questionIds.mapIndexedNotNull { index, questionId ->
            val question = questionsById[questionId] ?: return@mapIndexedNotNull null
            InterviewSessionQuestionEntity(
                interviewSessionId = sessionId,
                questionId = question.id,
                parentSessionQuestionId = null,
                promptText = question.title,
                questionSourceType = SOURCE_TYPE_CATALOG_SEED,
                orderIndex = index + 1,
                isFollowUp = false,
                depth = 0,
                categoryName = categoryNameById[question.categoryId],
                tagsJson = tagsJsonByQuestionId[question.id],
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private fun maybeInsertFollowUp(sessionId: Long, answeredRow: InterviewSessionQuestionEntity, now: java.time.Instant) {
        val parentQuestionId = answeredRow.questionId ?: return
        val existingRows = interviewSessionQuestionRepository.findByInterviewSessionIdOrderByOrderIndexAsc(sessionId)
        if (existingRows.any { it.parentSessionQuestionId == answeredRow.id }) {
            return
        }

        val edge = questionRelationshipRepository.findByParentQuestionIdOrderByDisplayOrderAscIdAsc(parentQuestionId)
            .firstOrNull { it.relationshipType.lowercase() == RELATIONSHIP_TYPE_FOLLOW_UP }
            ?: return
        val childQuestion = questionRepository.findByIdAndIsActiveTrue(edge.childQuestionId) ?: return
        if (existingRows.any { it.questionId == childQuestion.id && it.parentSessionQuestionId == answeredRow.id }) {
            return
        }

        val shiftedRows = existingRows.filter { it.orderIndex > answeredRow.orderIndex }.map { row ->
            InterviewSessionQuestionEntity(
                id = row.id,
                interviewSessionId = row.interviewSessionId,
                questionId = row.questionId,
                parentSessionQuestionId = row.parentSessionQuestionId,
                promptText = row.promptText,
                questionSourceType = row.questionSourceType,
                orderIndex = row.orderIndex + 1,
                isFollowUp = row.isFollowUp,
                depth = row.depth,
                categoryName = row.categoryName,
                tagsJson = row.tagsJson,
                answerAttemptId = row.answerAttemptId,
                createdAt = row.createdAt,
                updatedAt = now,
            )
        }
        if (shiftedRows.isNotEmpty()) {
            interviewSessionQuestionRepository.saveAll(shiftedRows)
        }

        val categoryName = categoryRepository.findById(childQuestion.categoryId).orElse(null)?.name
        val tagsJson = loadTagsJson(listOf(childQuestion.id))[childQuestion.id]
        interviewSessionQuestionRepository.save(
            InterviewSessionQuestionEntity(
                interviewSessionId = sessionId,
                questionId = childQuestion.id,
                parentSessionQuestionId = answeredRow.id,
                promptText = childQuestion.title,
                questionSourceType = SOURCE_TYPE_CATALOG_FOLLOW_UP,
                orderIndex = answeredRow.orderIndex + 1,
                isFollowUp = true,
                depth = answeredRow.depth + 1,
                categoryName = categoryName,
                tagsJson = tagsJson,
                createdAt = now,
                updatedAt = now,
            ),
        )
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
        if (requestedResumeVersionId != null) {
            val version = resumeVersionRepository.findById(requestedResumeVersionId)
                .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resumeVersionId: $requestedResumeVersionId") }
            resumeRepository.findByIdAndUserId(version.resumeId, userId)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resumeVersionId: $requestedResumeVersionId")
            return version.id
        }

        val resume = resumeRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId).firstOrNull() ?: return null
        val versions = resumeVersionRepository.findByResumeIdOrderByVersionNoAsc(resume.id)
        return versions.findLast { it.isActive }?.id ?: versions.lastOrNull()?.id
    }

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
        const val SESSION_TYPE_RESUME_MOCK = "resume_mock"
        const val SESSION_TYPE_REVIEW_MOCK = "review_mock"
        const val SESSION_TYPE_TOPIC_MOCK = "topic_mock"
        const val RELATIONSHIP_TYPE_FOLLOW_UP = "follow_up"
        const val REVIEW_STATUS_PENDING = "pending"
        val SUPPORTED_SESSION_TYPES = setOf(SESSION_TYPE_RESUME_MOCK, SESSION_TYPE_REVIEW_MOCK, SESSION_TYPE_TOPIC_MOCK)
    }
}
