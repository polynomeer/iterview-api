package com.example.interviewplatform.dailycard.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.dailycard.entity.DailyCardEntity
import com.example.interviewplatform.dailycard.repository.DailyCardRepository
import com.example.interviewplatform.question.entity.QuestionCompanyEntity
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.question.repository.QuestionCompanyRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.review.entity.ReviewQueueEntity
import com.example.interviewplatform.review.repository.ReviewQueueRepository
import com.example.interviewplatform.user.repository.UserTargetCompanyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class DailyCardGenerationService(
    private val dailyCardRepository: DailyCardRepository,
    private val reviewQueueRepository: ReviewQueueRepository,
    private val questionRepository: QuestionRepository,
    private val questionCompanyRepository: QuestionCompanyRepository,
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val userTargetCompanyRepository: UserTargetCompanyRepository,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val clockService: ClockService,
) {
    @Transactional
    fun generateForToday(userId: Long): GeneratedDailySelection {
        val now = clockService.now()
        val today = LocalDate.ofInstant(now, java.time.ZoneOffset.UTC)

        val existingCards = dailyCardRepository.findByUserIdAndCardDateOrderByCreatedAtAsc(userId, today)
        if (existingCards.isNotEmpty()) {
            return GeneratedDailySelection(
                allCards = existingCards,
                mainCard = existingCards.firstOrNull { !isRetryCard(it) } ?: existingCards.firstOrNull(),
            )
        }

        val activeQuestionsById = questionRepository.findByIsActiveTrue().associateBy { it.id }
        if (activeQuestionsById.isEmpty()) {
            return GeneratedDailySelection(emptyList(), null)
        }

        val pendingRetry = resolveRetryCandidates(userId, now, activeQuestionsById.keys)

        val selectedCards = mutableListOf<DailyCardEntity>()
        val selectedQuestionIds = mutableSetOf<Long>()
        var mainCard: DailyCardEntity? = null

        if (pendingRetry.isNotEmpty()) {
            val mainRetry = pendingRetry.first()
            mainCard = newCard(
                userId = userId,
                questionId = mainRetry.questionId,
                cardDate = today,
                cardType = CARD_TYPE_RETRY,
                sourceReason = SOURCE_REASON_RETRY_QUEUE,
                now = now,
            )
            selectedCards += mainCard
            selectedQuestionIds += mainRetry.questionId

            pendingRetry.drop(1).forEach { queue ->
                if (selectedQuestionIds.add(queue.questionId)) {
                    selectedCards += newCard(
                        userId = userId,
                        questionId = queue.questionId,
                        cardDate = today,
                        cardType = CARD_TYPE_RETRY,
                        sourceReason = SOURCE_REASON_RETRY_QUEUE,
                        now = now,
                    )
                }
            }
        }

        if (mainCard == null) {
            val fallback = pickFallbackQuestion(userId, activeQuestionsById.values, selectedQuestionIds)
            if (fallback != null) {
                mainCard = newCard(
                    userId = userId,
                    questionId = fallback.id,
                    cardDate = today,
                    cardType = CARD_TYPE_DAILY,
                    sourceReason = SOURCE_REASON_RECOMMENDATION,
                    now = now,
                )
                selectedCards += mainCard
                selectedQuestionIds += fallback.id
            }
        }

        val saved = if (selectedCards.isEmpty()) emptyList() else dailyCardRepository.saveAll(selectedCards)
        return GeneratedDailySelection(allCards = saved, mainCard = saved.find { it.questionId == mainCard?.questionId })
    }

    @Transactional(readOnly = true)
    fun resolveRetryCandidatesForHome(userId: Long): List<ReviewQueueEntity> {
        val now = clockService.now()
        val activeIds = questionRepository.findByIsActiveTrue().map { it.id }.toSet()
        return resolveRetryCandidates(userId, now, activeIds)
    }

    private fun resolveRetryCandidates(
        userId: Long,
        now: Instant,
        activeQuestionIds: Set<Long>,
    ): List<ReviewQueueEntity> {
        val pending = reviewQueueRepository.findByUserIdAndStatusAndScheduledForLessThanEqualOrderByScheduledForAscPriorityDesc(
            userId,
            STATUS_PENDING,
            now,
        )
        if (pending.isEmpty()) {
            return emptyList()
        }

        val archivedQuestionIds = userQuestionProgressRepository
            .findByUserIdAndCurrentStatusOrderByArchivedAtDesc(userId, STATUS_ARCHIVED)
            .map { it.questionId }
            .toSet()

        return pending.filter { row ->
            row.questionId in activeQuestionIds && row.questionId !in archivedQuestionIds
        }
    }

    private fun pickFallbackQuestion(
        userId: Long,
        activeQuestions: Collection<QuestionEntity>,
        excludedQuestionIds: Set<Long>,
    ): QuestionEntity? {
        val candidates = activeQuestions.filter { it.id !in excludedQuestionIds }
        if (candidates.isEmpty()) {
            return null
        }

        val hasActiveResume = hasActiveResumeVersion(userId)
        val targetCompanyIds = userTargetCompanyRepository.findByIdUserIdOrderByPriorityOrderAsc(userId)
            .map { it.id.companyId }
            .toSet()

        val companyEdges = questionCompanyRepository.findByIdQuestionIdIn(candidates.map { it.id })
            .groupBy { it.id.questionId }

        return candidates.sortedWith(
            compareByDescending<QuestionEntity> { targetCompanyScore(companyEdges[it.id].orEmpty(), targetCompanyIds) }
                .thenByDescending { resumeFitScore(it, hasActiveResume) }
                .thenBy { difficultyRank(it.difficultyLevel) }
                .thenByDescending { it.createdAt }
                .thenBy { it.id },
        ).firstOrNull()
    }

    private fun hasActiveResumeVersion(userId: Long): Boolean {
        val resumeIds = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId).map { it.id }
        if (resumeIds.isEmpty()) {
            return false
        }
        return resumeVersionRepository.findByResumeIdInOrderByResumeIdAscVersionNoAsc(resumeIds).any { it.isActive }
    }

    private fun targetCompanyScore(edges: List<QuestionCompanyEntity>, targetCompanyIds: Set<Long>): Int {
        if (targetCompanyIds.isEmpty()) {
            return 0
        }
        return edges.count { it.id.companyId in targetCompanyIds }
    }

    private fun resumeFitScore(question: QuestionEntity, hasActiveResume: Boolean): Int {
        val technical = question.questionType.equals("technical", ignoreCase = true)
        return when {
            hasActiveResume && technical -> 1
            !hasActiveResume && !technical -> 1
            else -> 0
        }
    }

    private fun difficultyRank(difficulty: String): Int = when (difficulty.uppercase()) {
        "EASY" -> 0
        "MEDIUM" -> 1
        "HARD" -> 2
        else -> 3
    }

    private fun isRetryCard(card: DailyCardEntity): Boolean =
        card.cardType.equals(CARD_TYPE_RETRY, ignoreCase = true) ||
            card.sourceReason.contains("retry", ignoreCase = true)

    private fun newCard(
        userId: Long,
        questionId: Long,
        cardDate: LocalDate,
        cardType: String,
        sourceReason: String,
        now: Instant,
    ): DailyCardEntity = DailyCardEntity(
        userId = userId,
        questionId = questionId,
        cardDate = cardDate,
        cardType = cardType,
        sourceReason = sourceReason,
        status = STATUS_NEW,
        deliveredAt = now,
        openedAt = null,
        createdAt = now,
    )

    data class GeneratedDailySelection(
        val allCards: List<DailyCardEntity>,
        val mainCard: DailyCardEntity?,
    )

    private companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ARCHIVED = "archived"
        const val STATUS_NEW = "new"
        const val CARD_TYPE_DAILY = "daily"
        const val CARD_TYPE_RETRY = "retry"
        const val SOURCE_REASON_RETRY_QUEUE = "retry_queue"
        const val SOURCE_REASON_RECOMMENDATION = "recommendation"
    }
}
