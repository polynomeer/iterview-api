package com.example.interviewplatform.skill.repository

import com.example.interviewplatform.skill.entity.SkillCategoryScoreEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.Instant

interface SkillCategoryScoreRepository : JpaRepository<SkillCategoryScoreEntity, Long> {
    fun findByUserIdOrderByScoreDesc(userId: Long): List<SkillCategoryScoreEntity>

    fun findByUserIdAndSkillCategoryId(userId: Long, skillCategoryId: Long): SkillCategoryScoreEntity?

    @Modifying
    @Query(
        value = """
            insert into skill_category_scores (
                user_id,
                skill_category_id,
                score,
                answered_question_count,
                weak_question_count,
                benchmark_score,
                gap_score,
                calculated_at,
                created_at,
                updated_at
            ) values (
                :userId,
                :skillCategoryId,
                :score,
                :answeredQuestionCount,
                :weakQuestionCount,
                :benchmarkScore,
                :gapScore,
                :calculatedAt,
                :createdAt,
                :updatedAt
            )
            on conflict (user_id, skill_category_id) do update
            set score = excluded.score,
                answered_question_count = excluded.answered_question_count,
                weak_question_count = excluded.weak_question_count,
                benchmark_score = excluded.benchmark_score,
                gap_score = excluded.gap_score,
                calculated_at = excluded.calculated_at,
                updated_at = excluded.updated_at
        """,
        nativeQuery = true,
    )
    fun upsert(
        userId: Long,
        skillCategoryId: Long,
        score: BigDecimal,
        answeredQuestionCount: Int,
        weakQuestionCount: Int,
        benchmarkScore: BigDecimal?,
        gapScore: BigDecimal?,
        calculatedAt: Instant,
        createdAt: Instant,
        updatedAt: Instant,
    ): Int
}
