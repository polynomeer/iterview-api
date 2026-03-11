package com.example.interviewplatform.skill.service

import com.example.interviewplatform.answer.repository.AnswerAnalysisRepository
import com.example.interviewplatform.answer.repository.AnswerAttemptRepository
import com.example.interviewplatform.answer.repository.AnswerScoreRepository
import com.example.interviewplatform.question.repository.QuestionSkillMappingRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.skill.dto.SkillGapItemDto
import com.example.interviewplatform.skill.dto.SkillProgressItemDto
import com.example.interviewplatform.skill.dto.SkillRadarCategoryDto
import com.example.interviewplatform.skill.dto.SkillRadarResponseDto
import com.example.interviewplatform.skill.entity.SkillCategoryEntity
import com.example.interviewplatform.skill.entity.SkillCategoryScoreEntity
import com.example.interviewplatform.skill.repository.CareerBenchmarkRepository
import com.example.interviewplatform.skill.repository.SkillCategoryRepository
import com.example.interviewplatform.skill.repository.SkillCategoryScoreRepository
import com.example.interviewplatform.skill.repository.SkillRepository
import com.example.interviewplatform.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SkillRadarService(
    private val skillCategoryRepository: SkillCategoryRepository,
    private val skillRepository: SkillRepository,
    private val skillCategoryScoreRepository: SkillCategoryScoreRepository,
    private val careerBenchmarkRepository: CareerBenchmarkRepository,
    private val questionSkillMappingRepository: QuestionSkillMappingRepository,
    private val answerAttemptRepository: AnswerAttemptRepository,
    private val answerScoreRepository: AnswerScoreRepository,
    private val answerAnalysisRepository: AnswerAnalysisRepository,
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
    private val userProfileRepository: UserProfileRepository,
    private val skillScoreCalculator: SkillScoreCalculator,
) {
    @Transactional
    fun getRadar(userId: Long): SkillRadarResponseDto {
        val recalculated = recalculate(userId)
        return SkillRadarResponseDto(
            categories = recalculated.map(::toRadarCategoryDto),
            updatedAt = recalculated.maxOfOrNull { it.calculatedAt } ?: Instant.now(),
        )
    }

    @Transactional
    fun getGaps(userId: Long): List<SkillGapItemDto> = recalculate(userId)
        .sortedWith(compareByDescending<SkillCategoryScoreEntity> { it.gapScore?.toDouble() ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.skillCategoryId })
        .map(::toGapItemDto)

    @Transactional
    fun getProgress(userId: Long): List<SkillProgressItemDto> = recalculate(userId)
        .sortedBy { it.skillCategoryId }
        .map(::toProgressItemDto)

    private fun recalculate(userId: Long): List<SkillCategoryScoreEntity> {
        val categories = skillCategoryRepository.findAll().sortedBy { it.displayOrder }
        if (categories.isEmpty()) {
            return emptyList()
        }
        val existingByCategoryId = skillCategoryScoreRepository.findByUserIdOrderByScoreDesc(userId)
            .associateBy { it.skillCategoryId }

        val profile = userProfileRepository.findById(userId).orElse(null)
        val benchmarkByCategoryId = profile?.jobRoleId?.let { jobRoleId ->
            val experienceBand = skillScoreCalculator.experienceBandFor(profile.yearsOfExperience)
            careerBenchmarkRepository.findByJobRoleIdAndExperienceBandCode(jobRoleId, experienceBand)
                .associateBy { it.skillCategoryId }
        }.orEmpty()

        val skillsById = skillRepository.findAll().associateBy { it.id }
        val mappingsByCategoryId = questionSkillMappingRepository.findAll()
            .groupBy { mapping -> skillsById[mapping.skillId]?.skillCategoryId }
            .filterKeys { it != null }
            .mapKeys { it.key!! }

        val latestAttemptsByQuestionId = answerAttemptRepository.findByUserIdOrderBySubmittedAtDesc(userId)
            .distinctBy { it.questionId }
            .associateBy { it.questionId }
        val answerScoresByAttemptId = answerScoreRepository.findAllById(latestAttemptsByQuestionId.values.map { it.id })
            .associateBy { it.answerAttemptId }
        val answerAnalysesByAttemptId = answerAnalysisRepository.findByAnswerAttemptIdIn(latestAttemptsByQuestionId.values.map { it.id })
            .associateBy { it.answerAttemptId }
        val progressByQuestionId = userQuestionProgressRepository.findByUserIdAndQuestionIdIn(userId, latestAttemptsByQuestionId.keys.toList())
            .associateBy { it.questionId }
        val now = Instant.now()

        categories.forEach { category ->
            val mappings = mappingsByCategoryId[category.id].orEmpty()
            val categoryQuestionIds = mappings.map { it.questionId }.distinct()
            val attempts = categoryQuestionIds.mapNotNull { latestAttemptsByQuestionId[it] }
            val scores = attempts.mapNotNull { answerScoresByAttemptId[it.id] }
            val analyses = attempts.mapNotNull { answerAnalysesByAttemptId[it.id] }
            val progresses = categoryQuestionIds.mapNotNull { progressByQuestionId[it] }

            val answerQualityAvg = skillScoreCalculator.average(scores.map { it.totalScore.toDouble() })
            val reviewCompletionRate = if (progresses.isEmpty()) 0.0 else progresses.count { it.currentStatus != "retry_pending" }.toDouble() / progresses.size
            val recencyWeight = skillScoreCalculator.recencyWeight(attempts.maxOfOrNull { it.submittedAt }, now)
            val confidenceAvg = skillScoreCalculator.average(analyses.mapNotNull { it.confidenceScore?.toDouble() }) / 100.0
            val depthCoverage = skillScoreCalculator.average(analyses.map { it.depthScore.toDouble() }) / 100.0
            val finalScore = skillScoreCalculator.calculateScore(
                answerQualityAverage = answerQualityAvg,
                reviewCompletionRate = reviewCompletionRate,
                recencyWeight = recencyWeight,
                confidenceAverage = confidenceAvg,
                depthCoverage = depthCoverage,
            )
            val benchmark = benchmarkByCategoryId[category.id]?.benchmarkScore
            val gap = skillScoreCalculator.calculateGap(benchmark, finalScore)
            val existing = existingByCategoryId[category.id]
            skillCategoryScoreRepository.upsert(
                userId = userId,
                skillCategoryId = category.id,
                score = finalScore,
                answeredQuestionCount = progresses.count { it.totalAttemptCount > 0 },
                weakQuestionCount = progresses.count { it.currentStatus == "retry_pending" || (it.latestScore?.toDouble() ?: 0.0) < 60.0 },
                benchmarkScore = benchmark,
                gapScore = gap,
                calculatedAt = now,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        }
        return skillCategoryScoreRepository.findByUserIdOrderByScoreDesc(userId)
    }

    private fun toRadarCategoryDto(score: SkillCategoryScoreEntity): SkillRadarCategoryDto {
        val category = skillCategoryRepository.findById(score.skillCategoryId).orElseThrow()
        return SkillRadarCategoryDto(
            categoryCode = category.code,
            label = category.name,
            score = score.score,
            benchmarkScore = score.benchmarkScore,
            gapScore = score.gapScore,
        )
    }

    private fun toGapItemDto(score: SkillCategoryScoreEntity): SkillGapItemDto {
        val category = skillCategoryRepository.findById(score.skillCategoryId).orElseThrow()
        return SkillGapItemDto(
            categoryCode = category.code,
            label = category.name,
            score = score.score,
            benchmarkScore = score.benchmarkScore,
            gapScore = score.gapScore,
        )
    }

    private fun toProgressItemDto(score: SkillCategoryScoreEntity): SkillProgressItemDto {
        val category = skillCategoryRepository.findById(score.skillCategoryId).orElseThrow()
        return SkillProgressItemDto(
            categoryCode = category.code,
            label = category.name,
            score = score.score,
            benchmarkScore = score.benchmarkScore,
            gapScore = score.gapScore,
            answeredQuestionCount = score.answeredQuestionCount,
            weakQuestionCount = score.weakQuestionCount,
            calculatedAt = score.calculatedAt,
        )
    }

}
