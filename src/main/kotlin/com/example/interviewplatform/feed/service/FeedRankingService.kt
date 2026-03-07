package com.example.interviewplatform.feed.service

import com.example.interviewplatform.question.entity.QuestionCompanyEntity
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.question.entity.UserQuestionProgressEntity
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class FeedRankingService {
    fun rankPopular(
        questions: List<QuestionEntity>,
        progressByQuestionId: Map<Long, UserQuestionProgressEntity>,
        limit: Int,
    ): List<Long> = questions
        .sortedWith(
            compareByDescending<QuestionEntity> { progressByQuestionId[it.id]?.totalAttemptCount ?: 0 }
                .thenByDescending { progressByQuestionId[it.id]?.bestScore ?: BigDecimal.ZERO }
                .thenByDescending { it.createdAt },
        )
        .take(limit)
        .map { it.id }

    fun rankTrending(
        questions: List<QuestionEntity>,
        companyEdgesByQuestionId: Map<Long, List<QuestionCompanyEntity>>,
        limit: Int,
    ): List<Long> = questions
        .sortedWith(
            compareByDescending<QuestionEntity> { trendingScore(companyEdgesByQuestionId[it.id].orEmpty()) }
                .thenByDescending { it.createdAt },
        )
        .take(limit)
        .map { it.id }

    fun rankCompanyRelated(
        questions: List<QuestionEntity>,
        companyEdgesByQuestionId: Map<Long, List<QuestionCompanyEntity>>,
        targetCompanyIds: Set<Long>,
        limit: Int,
    ): List<Long> {
        if (targetCompanyIds.isEmpty()) {
            return emptyList()
        }

        return questions
            .mapNotNull { question ->
                val matchedEdges = companyEdgesByQuestionId[question.id].orEmpty()
                    .filter { targetCompanyIds.contains(it.id.companyId) }
                if (matchedEdges.isEmpty()) {
                    return@mapNotNull null
                }

                val maxRelevance = matchedEdges.maxOf { it.relevanceScore }
                val tieBreaker = matchedEdges.count { it.isTrendingRecent }
                RankedCompanyMatch(question = question, score = maxRelevance, tieBreaker = tieBreaker)
            }
            .sortedWith(
                compareByDescending<RankedCompanyMatch> { it.score }
                    .thenByDescending { it.tieBreaker }
                    .thenByDescending { it.question.createdAt },
            )
            .take(limit)
            .map { it.question.id }
    }

    private fun trendingScore(companyEdges: List<QuestionCompanyEntity>): Int {
        val trending = companyEdges.count { it.isTrendingRecent }
        val frequent = companyEdges.count { it.isPastFrequent }
        return (trending * 2) + frequent
    }

    private data class RankedCompanyMatch(
        val question: QuestionEntity,
        val score: BigDecimal,
        val tieBreaker: Int,
    )
}
