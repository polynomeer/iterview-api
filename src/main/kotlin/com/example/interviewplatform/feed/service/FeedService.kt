package com.example.interviewplatform.feed.service

import com.example.interviewplatform.feed.dto.FeedDto
import com.example.interviewplatform.feed.dto.FeedQuestionCardDto
import com.example.interviewplatform.feed.mapper.FeedMapper
import com.example.interviewplatform.question.dto.UserProgressSummaryDto
import com.example.interviewplatform.question.entity.QuestionCompanyEntity
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.question.entity.UserQuestionProgressEntity
import com.example.interviewplatform.question.repository.CategoryRepository
import com.example.interviewplatform.question.repository.QuestionCompanyRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.QuestionTagRepository
import com.example.interviewplatform.question.repository.TagRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.user.entity.CompanyEntity
import com.example.interviewplatform.user.repository.CompanyRepository
import com.example.interviewplatform.user.repository.UserTargetCompanyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FeedService(
    private val questionRepository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
    private val questionCompanyRepository: QuestionCompanyRepository,
    private val questionTagRepository: QuestionTagRepository,
    private val tagRepository: TagRepository,
    private val companyRepository: CompanyRepository,
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val userTargetCompanyRepository: UserTargetCompanyRepository,
    private val feedRankingService: FeedRankingService,
) {
    @Transactional(readOnly = true)
    fun getFeed(userId: Long): FeedDto {
        val questions = questionRepository.findByIsActiveTrue()
        if (questions.isEmpty()) {
            return FeedDto(popular = emptyList(), trending = emptyList(), companyRelated = emptyList())
        }

        val questionIds = questions.map { it.id }
        val categoryById = categoryRepository.findAllById(questions.map { it.categoryId }.distinct()).associateBy { it.id }

        val companyEdgesByQuestionId = questionCompanyRepository.findByIdQuestionIdIn(questionIds).groupBy { it.id.questionId }
        val companyById = companyRepository.findAllById(
            companyEdgesByQuestionId.values.flatten().map { it.id.companyId }.distinct(),
        ).associateBy { it.id }

        val tagEdges = questionTagRepository.findByIdQuestionIdIn(questionIds)
        val tagById = tagRepository.findAllById(tagEdges.map { it.id.tagId }.distinct()).associateBy { it.id }
        val tagsByQuestionId = tagEdges.groupBy { it.id.questionId }.mapValues { (_, edges) ->
            edges.mapNotNull { edge -> tagById[edge.id.tagId]?.name }
        }

        val progressByQuestionId = userQuestionProgressRepository
            .findByUserIdAndQuestionIdIn(userId, questionIds)
            .associateBy { it.questionId }

        val targetCompanyIds = userTargetCompanyRepository.findByIdUserIdOrderByPriorityOrderAsc(userId)
            .map { it.id.companyId }
            .toSet()

        val popularIds = feedRankingService.rankPopular(questions, progressByQuestionId, SECTION_LIMIT)
        val trendingIds = feedRankingService.rankTrending(questions, companyEdgesByQuestionId, SECTION_LIMIT)
        val companyRelatedIds = feedRankingService.rankCompanyRelated(
            questions = questions,
            companyEdgesByQuestionId = companyEdgesByQuestionId,
            targetCompanyIds = targetCompanyIds,
            limit = SECTION_LIMIT,
        )

        val questionById = questions.associateBy { it.id }
        return FeedDto(
            popular = toCards(popularIds, questionById, categoryById.mapValues { it.value.name }, companyEdgesByQuestionId, companyById, tagsByQuestionId, progressByQuestionId),
            trending = toCards(trendingIds, questionById, categoryById.mapValues { it.value.name }, companyEdgesByQuestionId, companyById, tagsByQuestionId, progressByQuestionId),
            companyRelated = toCards(companyRelatedIds, questionById, categoryById.mapValues { it.value.name }, companyEdgesByQuestionId, companyById, tagsByQuestionId, progressByQuestionId),
        )
    }

    private fun toCards(
        questionIds: List<Long>,
        questionById: Map<Long, QuestionEntity>,
        categoryNameById: Map<Long, String>,
        companyEdgesByQuestionId: Map<Long, List<QuestionCompanyEntity>>,
        companyById: Map<Long, CompanyEntity>,
        tagsByQuestionId: Map<Long, List<String>>,
        progressByQuestionId: Map<Long, UserQuestionProgressEntity>,
    ): List<FeedQuestionCardDto> = questionIds.mapNotNull { questionId ->
        val question = questionById[questionId] ?: return@mapNotNull null

        val companies = companyEdgesByQuestionId[questionId].orEmpty()
            .sortedByDescending { it.relevanceScore }
            .mapNotNull { edge -> companyById[edge.id.companyId] }

        FeedMapper.toCard(
            question = question,
            categoryName = categoryNameById[question.categoryId],
            companies = companies,
            tags = tagsByQuestionId[questionId].orEmpty(),
            progressSummary = progressByQuestionId[questionId]?.let(::toProgressSummary),
        )
    }

    private fun toProgressSummary(progress: UserQuestionProgressEntity): UserProgressSummaryDto = UserProgressSummaryDto(
        currentStatus = progress.currentStatus,
        latestScore = progress.latestScore,
        bestScore = progress.bestScore,
        totalAttemptCount = progress.totalAttemptCount,
        lastAnsweredAt = progress.lastAnsweredAt,
        nextReviewAt = progress.nextReviewAt,
        masteryLevel = progress.masteryLevel,
    )

    private companion object {
        const val SECTION_LIMIT = 10
    }
}
