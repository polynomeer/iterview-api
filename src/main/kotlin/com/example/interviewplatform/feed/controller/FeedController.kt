package com.example.interviewplatform.feed.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.feed.dto.FeedDto
import com.example.interviewplatform.feed.service.FeedService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/feed")
class FeedController(
    private val feedService: FeedService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    fun getFeed(): FeedDto = feedService.getFeed(currentUserProvider.currentUserId())
}
