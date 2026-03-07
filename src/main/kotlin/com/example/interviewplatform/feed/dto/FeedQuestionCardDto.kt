package com.example.interviewplatform.feed.dto

import com.example.interviewplatform.question.dto.UserProgressSummaryDto

data class FeedQuestionCardDto(
    val questionId: Long,
    val title: String,
    val category: String?,
    val difficulty: String,
    val relatedCompanies: List<FeedCompanyDto>,
    val tags: List<String>,
    val userProgressSummary: UserProgressSummaryDto?,
)
