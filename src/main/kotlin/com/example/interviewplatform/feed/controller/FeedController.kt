package com.example.interviewplatform.feed.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.feed.dto.FeedDto
import com.example.interviewplatform.feed.service.FeedService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Feed")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/feed")
class FeedController(
    private val feedService: FeedService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "Get feed sections")
    fun getFeed(): FeedDto = feedService.getFeed(currentUserProvider.currentUserId())
}
