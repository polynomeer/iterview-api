package com.example.interviewplatform.feed.service

import com.example.interviewplatform.feed.dto.FeedDto
import org.springframework.stereotype.Service

@Service
class FeedService {
    fun getFeed(): FeedDto = FeedDto(
        popular = emptyList(),
        trending = emptyList(),
        companyRelated = emptyList(),
    )
}
