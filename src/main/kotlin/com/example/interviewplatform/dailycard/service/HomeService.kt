package com.example.interviewplatform.dailycard.service

import com.example.interviewplatform.dailycard.dto.HomeQuestionDto
import com.example.interviewplatform.dailycard.dto.HomeResumeRiskQuestionDto
import com.example.interviewplatform.dailycard.dto.HomeResponseDto
import com.example.interviewplatform.dailycard.dto.HomeRetryQuestionDto
import com.example.interviewplatform.dailycard.dto.HomeSkillRadarPreviewDto
import com.example.interviewplatform.dailycard.dto.HomeSummaryStatsDto
import com.example.interviewplatform.dailycard.dto.HomeWeakSkillDto
import com.example.interviewplatform.question.dto.LearningMaterialDto
import com.example.interviewplatform.question.mapper.QuestionMapper
import com.example.interviewplatform.question.repository.LearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionLearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.skill.service.SkillRadarService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HomeService(
    private val dailyCardGenerationService: DailyCardGenerationService,
    private val questionRepository: QuestionRepository,
    private val questionLearningMaterialRepository: QuestionLearningMaterialRepository,
    private val learningMaterialRepository: LearningMaterialRepository,
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val skillRadarService: SkillRadarService,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
) {
    @Transactional
    fun getHome(userId: Long): HomeResponseDto {
        val generated = dailyCardGenerationService.generateForToday(userId)
        val allTodayQuestions = questionRepository.findAllById(generated.allCards.map { it.questionId }).associateBy { it.id }

        val todayQuestion = generated.mainCard?.let { card ->
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

        val retryRows = dailyCardGenerationService.resolveRetryCandidatesForHome(userId)
        val retryQuestionById = questionRepository.findAllById(retryRows.map { it.questionId }).associateBy { it.id }
        val retryQuestions = retryRows
            .asSequence()
            .filter { it.questionId != todayQuestion?.questionId }
            .distinctBy { it.questionId }
            .mapNotNull { queue ->
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
            .toList()

        val materialQuestionIds = buildSet {
            todayQuestion?.let { add(it.questionId) }
            retryQuestions.forEach { add(it.questionId) }
        }
        val learningMaterials = loadLearningMaterials(materialQuestionIds.toList())
        val radar = skillRadarService.getRadar(userId)
        val gaps = skillRadarService.getGaps(userId)
        val resumeRiskPreview = loadResumeRiskPreview(userId)

        val summaryStats = HomeSummaryStatsDto(
            dailyQuestionCount = if (todayQuestion == null) 0 else 1,
            retryQuestionCount = retryQuestions.size,
            pendingReviewCount = retryRows.size,
            archivedQuestionCount = userQuestionProgressRepository
                .findByUserIdAndCurrentStatusOrderByArchivedAtDesc(userId, STATUS_ARCHIVED)
                .size,
        )

        return HomeResponseDto(
            todayQuestion = todayQuestion,
            retryQuestions = retryQuestions,
            skillRadarPreview = radar.categories.take(3).map {
                HomeSkillRadarPreviewDto(
                    categoryCode = it.categoryCode,
                    score = it.score,
                    gapScore = it.gapScore,
                )
            },
            weakSkillHighlights = gaps.take(3).map {
                HomeWeakSkillDto(
                    categoryCode = it.categoryCode,
                    label = it.label,
                    gapScore = it.gapScore,
                )
            },
            resumeRiskPreview = resumeRiskPreview,
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
            .map(QuestionMapper::toLearningMaterialDto)
    }

    private fun loadResumeRiskPreview(userId: Long): List<HomeResumeRiskQuestionDto> {
        val resume = resumeRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId).firstOrNull() ?: return emptyList()
        val version = resumeVersionRepository.findByResumeIdOrderByVersionNoAsc(resume.id).findLast { it.isActive }
            ?: resumeVersionRepository.findTopByResumeIdOrderByVersionNoDesc(resume.id)
            ?: return emptyList()
        val risks = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(version.id)
        if (risks.isEmpty()) {
            return emptyList()
        }
        val questionById = questionRepository.findAllById(risks.mapNotNull { it.linkedQuestionId }).associateBy { it.id }
        return risks.mapNotNull { risk ->
            val questionId = risk.linkedQuestionId ?: return@mapNotNull null
            val question = questionById[questionId] ?: return@mapNotNull null
            HomeResumeRiskQuestionDto(
                questionId = questionId,
                title = question.title,
                severity = risk.severity,
            )
        }.take(3)
    }

    private companion object {
        const val STATUS_ARCHIVED = "archived"
    }
}
