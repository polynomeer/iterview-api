package com.example.interviewplatform.review.service

import com.example.interviewplatform.answer.repository.AnswerScoreRepository
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.interview.repository.InterviewRecordAnswerRepository
import com.example.interviewplatform.interview.repository.InterviewRecordQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewRecordRepository
import com.example.interviewplatform.interview.repository.InterviewSessionQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewSessionRepository
import com.example.interviewplatform.interview.service.InterviewRecordQuestionAssetService
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.review.dto.ArchivedQuestionDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArchiveService(
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val questionRepository: QuestionRepository,
    private val interviewSessionRepository: InterviewSessionRepository,
    private val interviewSessionQuestionRepository: InterviewSessionQuestionRepository,
    private val interviewRecordRepository: InterviewRecordRepository,
    private val interviewRecordQuestionRepository: InterviewRecordQuestionRepository,
    private val interviewRecordAnswerRepository: InterviewRecordAnswerRepository,
    private val interviewRecordQuestionAssetService: InterviewRecordQuestionAssetService,
    private val answerScoreRepository: AnswerScoreRepository,
) {
    @Transactional
    fun listArchived(userId: Long): List<ArchivedQuestionDto> {
        val progressRows = userQuestionProgressRepository
            .findByUserIdAndCurrentStatusOrderByArchivedAtDesc(userId, STATUS_ARCHIVED)
        val sessions = interviewSessionRepository.findByUserIdOrderByStartedAtDesc(userId)
        val interviewRecords = interviewRecordRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val sessionRows = if (sessions.isEmpty()) {
            emptyList()
        } else {
            interviewSessionQuestionRepository.findByInterviewSessionIdInOrderByInterviewSessionIdAscOrderIndexAsc(sessions.map { it.id })
        }
        val importedQuestionsByRecordId = if (interviewRecords.isEmpty()) {
            emptyMap()
        } else {
            interviewRecords.associate { record ->
                record.id to interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(record.id)
            }
        }
        if (progressRows.isEmpty() && sessionRows.isEmpty() && importedQuestionsByRecordId.values.flatten().isEmpty()) {
            return emptyList()
        }

        val questionById = questionRepository.findAllById(
            (progressRows.map { it.questionId } + sessionRows.mapNotNull { it.questionId }).distinct(),
        ).associateBy { it.id }
        val sessionQuestionIds = sessionRows.map { it.id }.toSet()

        val progressItems = progressRows.mapNotNull { progress ->
            if (progress.sourceType == SOURCE_TYPE_INTERVIEW && progress.sourceSessionQuestionId in sessionQuestionIds) {
                return@mapNotNull null
            }
            val question = questionById[progress.questionId] ?: return@mapNotNull null
            val archivedAt = progress.archivedAt ?: return@mapNotNull null
            ArchivedQuestionDto(
                questionId = question.id,
                title = question.title,
                difficulty = question.difficultyLevel,
                archivedAt = archivedAt,
                bestScore = progress.bestScore,
                totalAttemptCount = progress.totalAttemptCount,
                sourceType = progress.sourceType,
                sourceLabel = progress.sourceLabel,
                sourceSessionId = progress.sourceSessionId,
                sourceSessionQuestionId = progress.sourceSessionQuestionId,
                isFollowUp = progress.isFollowUp,
            )
        }

        val sessionsById = sessions.associateBy { it.id }
        val scoreByAttemptId = answerScoreRepository.findAllById(sessionRows.mapNotNull { it.answerAttemptId })
            .associateBy { it.answerAttemptId }
        val sessionItems = sessionRows.groupBy { it.interviewSessionId }
            .flatMap { (sessionId, rows) ->
                val session = sessionsById[sessionId] ?: return@flatMap emptyList()
                askedRows(rows, session.status).map { row ->
                    val question = row.questionId?.let(questionById::get)
                    ArchivedQuestionDto(
                        questionId = row.questionId ?: row.id,
                        title = row.promptText ?: question?.title ?: "Interview Question",
                        difficulty = question?.difficultyLevel ?: UNKNOWN_DIFFICULTY,
                        archivedAt = row.updatedAt,
                        bestScore = row.answerAttemptId?.let(scoreByAttemptId::get)?.totalScore,
                        totalAttemptCount = if (row.answerAttemptId != null) 1 else 0,
                        sourceType = SOURCE_TYPE_INTERVIEW,
                        sourceLabel = SOURCE_LABEL_INTERVIEW,
                        sourceSessionId = row.interviewSessionId,
                        sourceSessionQuestionId = row.id,
                        isFollowUp = row.isFollowUp,
                    )
                }
            }

        val importedQuestionIds = importedQuestionsByRecordId.values.flatten().map { it.id }
        val importedAnswerByQuestionId = if (importedQuestionIds.isEmpty()) {
            emptyMap()
        } else {
            interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(importedQuestionIds)
                .associateBy { it.interviewRecordQuestionId }
        }
        val realInterviewItems = interviewRecords.flatMap { record ->
            val ensuredQuestions = interviewRecordQuestionAssetService.ensureLinkedQuestionAssets(
                record = record,
                questions = importedQuestionsByRecordId[record.id].orEmpty(),
                answersByQuestionId = importedAnswerByQuestionId,
                now = record.updatedAt,
            )
            ensuredQuestions.map { question ->
                ArchivedQuestionDto(
                    questionId = question.linkedQuestionId ?: question.id,
                    title = question.text,
                    difficulty = question.questionType.uppercase(),
                    archivedAt = question.updatedAt,
                    bestScore = null,
                    totalAttemptCount = if (importedAnswerByQuestionId.containsKey(question.id)) 1 else 0,
                    sourceType = SOURCE_TYPE_REAL_INTERVIEW,
                    sourceLabel = SOURCE_LABEL_REAL_INTERVIEW,
                    sourceSessionId = null,
                    sourceSessionQuestionId = null,
                    sourceInterviewRecordId = record.id,
                    sourceInterviewQuestionId = question.id,
                    isFollowUp = question.parentQuestionId != null,
                )
            }
        }

        return (progressItems + sessionItems + realInterviewItems)
            .sortedByDescending { it.archivedAt }
    }

    private fun askedRows(rows: List<InterviewSessionQuestionEntity>, sessionStatus: String): List<InterviewSessionQuestionEntity> {
        if (rows.isEmpty()) {
            return emptyList()
        }
        if (sessionStatus == STATUS_COMPLETED) {
            return rows
        }
        val currentRowId = rows.firstOrNull { it.answerAttemptId == null }?.id
        return rows.filter { it.answerAttemptId != null || it.id == currentRowId }
    }

    private companion object {
        const val STATUS_ARCHIVED = "archived"
        const val STATUS_COMPLETED = "completed"
        const val SOURCE_TYPE_INTERVIEW = "interview"
        const val SOURCE_LABEL_INTERVIEW = "Interview"
        const val SOURCE_TYPE_REAL_INTERVIEW = "real_interview"
        const val SOURCE_LABEL_REAL_INTERVIEW = "Real Interview"
        const val UNKNOWN_DIFFICULTY = "UNKNOWN"
    }
}
