package com.example.interviewplatform.dailycard.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.dailycard.dto.HomeQuestionDto
import com.example.interviewplatform.dailycard.dto.HomeResponseDto
import com.example.interviewplatform.dailycard.dto.HomeRetryQuestionDto
import com.example.interviewplatform.dailycard.dto.HomeSummaryStatsDto
import com.example.interviewplatform.dailycard.repository.DailyCardRepository
import com.example.interviewplatform.question.dto.LearningMaterialDto
import com.example.interviewplatform.question.repository.LearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionLearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class HomeService(
    private val dailyCardRepository: DailyCardRepository,
    private val questionRepository: QuestionRepository,
    private val questionLearningMaterialRepository: QuestionLearningMaterialRepository,
    private val learningMaterialRepository: LearningMaterialRepository,
    private val reviewQueueRepository: ReviewQueueRepository,
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val clockService: ClockService,
) {
    @Transactional(readOnly = true)
    fun getHome(userId: Long): HomeResponseDto {
        val todayCards = dailyCardRepository.findByUserIdAndCardDateOrderByCreatedAtAsc(userId, LocalDate.now())
        val allTodayQuestions = questionRepository.findAllById(todayCards.map { it.questionId }).associateBy { it.id }

        val dailyCandidates = todayCards.filterNot { isRetryCard(it.cardType, it.sourceReason) }
        val mainCard = (dailyCandidates.firstOrNull() ?: todayCards.firstOrNull())
        val todayQuestion = mainCard?.let { card ->
            allTodayQuestions[card.questionId]?.let { question ->
                HomeQuestionDto(
                    dailyCardId = card.id,
                    questionId = question.id,
                    title = question.title,
                    difficulty = question.difficultyLevel,
                    cardDate = card.cardDate,
                    cardType = card.cardType,
                    status = card.status,
                )
            }
        }

        val pendingRetryRows = reviewQueueRepository
            .findByUserIdAndStatusAndScheduledForLessThanEqualOrderByScheduledForAscPriorityDesc(
                userId,
                STATUS_PENDING,
                clockService.now(),
            )
        val retryQuestionById = questionRepository.findAllById(pendingRetryRows.map { it.questionId }).associateBy { it.id }
        val retryQuestions = pendingRetryRows.mapNotNull { queue ->
            retryQuestionById[queue.questionId]?.let { question ->
                HomeRetryQuestionDto(
                    reviewQueueId = queue.id,
                    questionId = question.id,
                    title = question.title,
                    difficulty = question.difficultyLevel,
                    priority = queue.priority,
                    scheduledFor = queue.scheduledFor,
                )
            }
        }

        val materialQuestionIds = buildSet {
            todayQuestion?.let { add(it.questionId) }
            retryQuestions.forEach { add(it.questionId) }
        }
        val learningMaterials = loadLearningMaterials(materialQuestionIds.toList())

        val summaryStats = HomeSummaryStatsDto(
            dailyQuestionCount = if (todayQuestion == null) 0 else 1,
            retryQuestionCount = retryQuestions.size,
            pendingReviewCount = pendingRetryRows.size,
            archivedQuestionCount = userQuestionProgressRepository
                .findByUserIdAndCurrentStatusOrderByArchivedAtDesc(userId, STATUS_ARCHIVED)
                .size,
        )

        return HomeResponseDto(
            todayQuestion = todayQuestion,
            retryQuestions = retryQuestions,
            learningMaterials = learningMaterials,
            summaryStats = summaryStats,
        )
    }

    private fun loadLearningMaterials(questionIds: List<Long>): List<LearningMaterialDto> {
        if (questionIds.isEmpty()) {
            return emptyList()
        }
        val edges = questionLearningMaterialRepository.findByIdQuestionIdIn(questionIds)
        if (edges.isEmpty()) {
            return emptyList()
        }
        val materialsById = learningMaterialRepository.findAllById(edges.map { it.id.learningMaterialId }.distinct())
            .associateBy { it.id }

        return edges
            .sortedByDescending { it.relevanceScore }
            .mapNotNull { edge -> materialsById[edge.id.learningMaterialId] }
            .distinctBy { it.id }
            .map {
                LearningMaterialDto(
                    id = it.id,
                    title = it.title,
                    materialType = it.materialType,
                    contentUrl = it.contentUrl,
                    sourceName = it.sourceName,
                )
            }
    }

    private fun isRetryCard(cardType: String, sourceReason: String): Boolean =
        cardType.equals("retry", ignoreCase = true) || sourceReason.contains("retry", ignoreCase = true)

    private companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ARCHIVED = "archived"
    }
}
