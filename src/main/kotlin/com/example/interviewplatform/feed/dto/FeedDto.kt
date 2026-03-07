package com.example.interviewplatform.feed.dto

data class FeedDto(
    val popular: List<FeedQuestionCardDto>,
    val trending: List<FeedQuestionCardDto>,
    val companyRelated: List<FeedQuestionCardDto>,
)
