package com.example.interviewplatform.review.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.review.dto.ReviewQueueActionResponseDto
import com.example.interviewplatform.review.dto.ReviewQueueItemDto
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ReviewQueueService(
    private val reviewQueueRepository: ReviewQueueRepository,
    private val questionRepository: QuestionRepository,
    private val clockService: ClockService,
) {
    @Transactional(readOnly = true)
    fun listPending(userId: Long): List<ReviewQueueItemDto> {
        val rows = reviewQueueRepository.findByUserIdAndStatusAndScheduledForLessThanEqualOrderByScheduledForAscPriorityDesc(
            userId,
            STATUS_PENDING,
            clockService.now(),
        )
        if (rows.isEmpty()) {
            return emptyList()
        }

        val questionById = questionRepository.findAllById(rows.map { it.questionId }).associateBy { it.id }
        return rows.mapNotNull { row ->
            val question = questionById[row.questionId] ?: return@mapNotNull null
            ReviewQueueItemDto(
                id = row.id,
                questionId = row.questionId,
                questionTitle = question.title,
                questionDifficulty = question.difficultyLevel,
                reasonType = row.reasonType,
                priority = row.priority,
                scheduledFor = row.scheduledFor,
                status = row.status,
            )
        }
    }

    @Transactional
    fun skip(userId: Long, queueId: Long): ReviewQueueActionResponseDto =
        updateStatus(userId, queueId, STATUS_SKIPPED)

    @Transactional
    fun done(userId: Long, queueId: Long): ReviewQueueActionResponseDto =
        updateStatus(userId, queueId, STATUS_DONE)

    private fun updateStatus(userId: Long, queueId: Long, nextStatus: String): ReviewQueueActionResponseDto {
        val now = clockService.now()
        val updated = reviewQueueRepository.updateStatusById(
            id = queueId,
            userId = userId,
            expectedStatus = STATUS_PENDING,
            status = nextStatus,
            updatedAt = now,
        )
        if (updated == 0) {
            reviewQueueRepository.findByIdAndUserId(queueId, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Review queue not found: $queueId")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Review queue is not pending: $queueId")
        }

        return ReviewQueueActionResponseDto(
            id = queueId,
            status = nextStatus,
            updatedAt = now,
        )
    }

    private companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_DONE = "done"
    }
}
