package com.example.interviewplatform.review.service

import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.review.dto.ArchivedQuestionDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArchiveService(
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val questionRepository: QuestionRepository,
) {
    @Transactional(readOnly = true)
    fun listArchived(userId: Long): List<ArchivedQuestionDto> {
        val progressRows = userQuestionProgressRepository
            .findByUserIdAndCurrentStatusOrderByArchivedAtDesc(userId, STATUS_ARCHIVED)
        if (progressRows.isEmpty()) {
            return emptyList()
        }

        val questionById = questionRepository.findAllById(progressRows.map { it.questionId }).associateBy { it.id }
        return progressRows.mapNotNull { progress ->
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
    }

    private companion object {
        const val STATUS_ARCHIVED = "archived"
    }
}
