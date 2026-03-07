package com.example.interviewplatform.review.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.review.dto.ArchivedQuestionDto
import com.example.interviewplatform.review.service.ArchiveService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Archive")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/archive")
class ArchiveController(
    private val archiveService: ArchiveService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "List archived questions")
    fun listArchived(): List<ArchivedQuestionDto> = archiveService.listArchived(currentUserProvider.currentUserId())
}
