package com.example.interviewplatform.review.controller

import com.example.interviewplatform.review.entity.ReviewQueueEntity
import com.example.interviewplatform.review.service.ReviewQueueService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/review-queue")
class ReviewQueueController(
    private val reviewQueueService: ReviewQueueService,
) {
    @GetMapping
    fun listPending(): List<ReviewQueueEntity> = reviewQueueService.listPending(DEFAULT_USER_ID)

    private companion object {
        const val DEFAULT_USER_ID = 1L
    }
}
