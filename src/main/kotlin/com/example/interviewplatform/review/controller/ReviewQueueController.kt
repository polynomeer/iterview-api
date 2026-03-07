package com.example.interviewplatform.review.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.review.dto.ReviewQueueActionResponseDto
import com.example.interviewplatform.review.dto.ReviewQueueItemDto
import com.example.interviewplatform.review.service.ReviewQueueService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/review-queue")
class ReviewQueueController(
    private val reviewQueueService: ReviewQueueService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    fun listPending(): List<ReviewQueueItemDto> = reviewQueueService.listPending(currentUserProvider.currentUserId())

    @PostMapping("/{queueId}/skip")
    fun skip(@PathVariable queueId: Long): ReviewQueueActionResponseDto =
        reviewQueueService.skip(currentUserProvider.currentUserId(), queueId)

    @PostMapping("/{queueId}/done")
    fun done(@PathVariable queueId: Long): ReviewQueueActionResponseDto =
        reviewQueueService.done(currentUserProvider.currentUserId(), queueId)
}
