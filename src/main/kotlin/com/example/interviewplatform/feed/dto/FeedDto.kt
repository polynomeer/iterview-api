package com.example.interviewplatform.feed.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Feed response containing popular, trending, and company-related question sections")
data class FeedDto(
    @field:Schema(description = "Questions ranked by aggregate popularity signals")
    val popular: List<FeedQuestionCardDto>,
    @field:Schema(description = "Questions ranked by recent company relevance signals")
    val trending: List<FeedQuestionCardDto>,
    @field:Schema(description = "Questions related to the user's target companies")
    val companyRelated: List<FeedQuestionCardDto>,
)
