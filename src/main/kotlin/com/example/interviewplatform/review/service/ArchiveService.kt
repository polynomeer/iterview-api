package com.example.interviewplatform.review.service

import com.example.interviewplatform.answer.repository.AnswerScoreRepository
import com.example.interviewplatform.interview.entity.InterviewSessionQuestionEntity
import com.example.interviewplatform.interview.repository.InterviewSessionQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewSessionRepository
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
    private val answerScoreRepository: AnswerScoreRepository,
) {
    @Transactional(readOnly = true)
    fun listArchived(userId: Long): List<ArchivedQuestionDto> {
        val progressRows = userQuestionProgressRepository
            .findByUserIdAndCurrentStatusOrderByArchivedAtDesc(userId, STATUS_ARCHIVED)
        val sessions = interviewSessionRepository.findByUserIdOrderByStartedAtDesc(userId)
        val sessionRows = if (sessions.isEmpty()) {
            emptyList()
        } else {
            interviewSessionQuestionRepository.findByInterviewSessionIdInOrderByInterviewSessionIdAscOrderIndexAsc(sessions.map { it.id })
        }
        if (progressRows.isEmpty() && sessionRows.isEmpty()) {
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

        return (progressItems + sessionItems)
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
        const val UNKNOWN_DIFFICULTY = "UNKNOWN"
    }
}
