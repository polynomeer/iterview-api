package com.example.interviewplatform.feed.dto

data class FeedDto(
    val popular: List<String>,
    val trending: List<String>,
    val companyRelated: List<String>,
)
