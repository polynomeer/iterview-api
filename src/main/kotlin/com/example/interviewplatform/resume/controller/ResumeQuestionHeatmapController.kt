package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.CreateResumeQuestionHeatmapLinkRequest
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapLinkDto
import com.example.interviewplatform.resume.dto.ResumeQuestionHeatmapOverlayTargetListDto
import com.example.interviewplatform.resume.dto.UpdateResumeQuestionHeatmapLinkRequest
import com.example.interviewplatform.resume.service.ResumeQuestionHeatmapService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "Resume Heatmap")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resume-versions/{versionId}/question-heatmap")
class ResumeQuestionHeatmapController(
    private val resumeQuestionHeatmapService: ResumeQuestionHeatmapService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "Get resume question heatmap for one resume version")
    fun getHeatmap(
        @PathVariable versionId: Long,
        @RequestParam(name = "scope", required = false, defaultValue = "all") scope: String,
        @RequestParam(name = "weakOnly", required = false, defaultValue = "false") weakOnly: Boolean,
        @RequestParam(name = "companyName", required = false) companyName: String?,
        @RequestParam(name = "interviewDateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) interviewDateFrom: LocalDate?,
        @RequestParam(name = "interviewDateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) interviewDateTo: LocalDate?,
    ): ResumeQuestionHeatmapDto =
        resumeQuestionHeatmapService.getHeatmap(
            userId = currentUserProvider.currentUserId(),
            versionId = versionId,
            scope = scope,
            weakOnly = weakOnly,
            companyName = companyName,
            interviewDateFrom = interviewDateFrom,
            interviewDateTo = interviewDateTo,
        )

    @GetMapping("/overlay-targets")
    @Operation(summary = "Get resume question heatmap overlay targets for one resume version")
    fun getOverlayTargets(
        @PathVariable versionId: Long,
        @RequestParam(name = "scope", required = false, defaultValue = "all") scope: String,
        @RequestParam(name = "weakOnly", required = false, defaultValue = "false") weakOnly: Boolean,
        @RequestParam(name = "companyName", required = false) companyName: String?,
        @RequestParam(name = "interviewDateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) interviewDateFrom: LocalDate?,
        @RequestParam(name = "interviewDateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) interviewDateTo: LocalDate?,
    ): ResumeQuestionHeatmapOverlayTargetListDto =
        resumeQuestionHeatmapService.getOverlayTargets(
            userId = currentUserProvider.currentUserId(),
            versionId = versionId,
            scope = scope,
            weakOnly = weakOnly,
            companyName = companyName,
            interviewDateFrom = interviewDateFrom,
            interviewDateTo = interviewDateTo,
        )

    @PostMapping("/links")
    @Operation(summary = "Create or replace one manual resume heatmap link")
    fun createLink(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: CreateResumeQuestionHeatmapLinkRequest,
    ): ResumeQuestionHeatmapLinkDto =
        resumeQuestionHeatmapService.createLink(
            userId = currentUserProvider.currentUserId(),
            versionId = versionId,
            request = request,
        )

    @PatchMapping("/links/{linkId}")
    @Operation(summary = "Update one manual resume heatmap link")
    fun updateLink(
        @PathVariable versionId: Long,
        @PathVariable linkId: Long,
        @Valid @RequestBody request: UpdateResumeQuestionHeatmapLinkRequest,
    ): ResumeQuestionHeatmapLinkDto =
        resumeQuestionHeatmapService.updateLink(
            userId = currentUserProvider.currentUserId(),
            versionId = versionId,
            linkId = linkId,
            request = request,
        )
}
