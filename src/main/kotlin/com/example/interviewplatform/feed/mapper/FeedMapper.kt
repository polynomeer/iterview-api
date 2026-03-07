package com.example.interviewplatform.feed.mapper

import com.example.interviewplatform.feed.dto.FeedCompanyDto
import com.example.interviewplatform.feed.dto.FeedQuestionCardDto
import com.example.interviewplatform.question.dto.UserProgressSummaryDto
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.user.entity.CompanyEntity

object FeedMapper {
    fun toCard(
        question: QuestionEntity,
        categoryName: String?,
        companies: List<CompanyEntity>,
        tags: List<String>,
        progressSummary: UserProgressSummaryDto?,
    ): FeedQuestionCardDto = FeedQuestionCardDto(
        questionId = question.id,
        title = question.title,
        category = categoryName,
        difficulty = question.difficultyLevel,
        relatedCompanies = companies.map { FeedCompanyDto(id = it.id, name = it.name) },
        tags = tags,
        userProgressSummary = progressSummary,
    )
}
