package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.ActivateResumeVersionResponse
import com.example.interviewplatform.resume.service.ResumeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Resume")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resume-versions")
class ResumeVersionController(
    private val resumeService: ResumeService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/{versionId}/activate")
    @Operation(summary = "Activate resume version")
    fun activate(@PathVariable versionId: Long): ActivateResumeVersionResponse =
        resumeService.activateResumeVersion(currentUserProvider.currentUserId(), versionId)
}
