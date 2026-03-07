package com.example.interviewplatform.review.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.review.dto.ArchivedQuestionDto
import com.example.interviewplatform.review.service.ArchiveService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/archive")
class ArchiveController(
    private val archiveService: ArchiveService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    fun listArchived(): List<ArchivedQuestionDto> = archiveService.listArchived(currentUserProvider.currentUserId())
}
