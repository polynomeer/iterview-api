package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.CreateResumeAnalysisRequest
import com.example.interviewplatform.resume.dto.CreateResumeAnalysisExportRequest
import com.example.interviewplatform.resume.dto.ResumeAnalysisDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisExportDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisListItemDto
import com.example.interviewplatform.resume.dto.UpdateResumeAnalysisSuggestionRequest
import com.example.interviewplatform.resume.service.ResumeAnalysisService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@Tag(name = "Resume Analysis")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resume-versions/{versionId}/analyses")
class ResumeAnalysisController(
    private val resumeAnalysisService: ResumeAnalysisService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "List persisted analyses for one resume version")
    fun listAnalyses(@PathVariable versionId: Long): List<ResumeAnalysisListItemDto> =
        resumeAnalysisService.listAnalyses(currentUserProvider.currentUserId(), versionId)

    @PostMapping
    @Operation(summary = "Create one job-aware analysis for a resume version")
    fun createAnalysis(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: CreateResumeAnalysisRequest,
    ): ResumeAnalysisDto = resumeAnalysisService.createAnalysis(currentUserProvider.currentUserId(), versionId, request)

    @GetMapping("/{analysisId}")
    @Operation(summary = "Get one persisted resume analysis")
    fun getAnalysis(
        @PathVariable versionId: Long,
        @PathVariable analysisId: Long,
    ): ResumeAnalysisDto = resumeAnalysisService.getAnalysis(currentUserProvider.currentUserId(), versionId, analysisId)

    @PatchMapping("/{analysisId}/suggestions/{suggestionId}")
    @Operation(summary = "Accept or unaccept one resume analysis suggestion")
    fun updateSuggestion(
        @PathVariable versionId: Long,
        @PathVariable analysisId: Long,
        @PathVariable suggestionId: Long,
        @Valid @RequestBody request: UpdateResumeAnalysisSuggestionRequest,
    ): ResumeAnalysisDto =
        resumeAnalysisService.updateSuggestion(
            userId = currentUserProvider.currentUserId(),
            versionId = versionId,
        analysisId = analysisId,
        suggestionId = suggestionId,
        request = request,
    )

    @GetMapping("/{analysisId}/exports")
    @Operation(summary = "List tailored resume exports for one analysis")
    fun listExports(
        @PathVariable versionId: Long,
        @PathVariable analysisId: Long,
    ): List<ResumeAnalysisExportDto> =
        resumeAnalysisService.listExports(currentUserProvider.currentUserId(), versionId, analysisId)

    @PostMapping("/{analysisId}/exports")
    @Operation(summary = "Create one tailored resume export for one analysis")
    fun createExport(
        @PathVariable versionId: Long,
        @PathVariable analysisId: Long,
        @Valid @RequestBody request: CreateResumeAnalysisExportRequest,
    ): ResumeAnalysisExportDto =
        resumeAnalysisService.createExport(
            userId = currentUserProvider.currentUserId(),
            versionId = versionId,
            analysisId = analysisId,
            request = request,
        )

    @GetMapping("/{analysisId}/exports/{exportId}/file")
    @Operation(summary = "Download one tailored resume export file")
    fun downloadExport(
        @PathVariable versionId: Long,
        @PathVariable analysisId: Long,
        @PathVariable exportId: Long,
    ): ResponseEntity<org.springframework.core.io.Resource> {
        val exported = resumeAnalysisService.downloadExport(
            userId = currentUserProvider.currentUserId(),
            versionId = versionId,
            analysisId = analysisId,
            exportId = exportId,
        )
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(exported.contentType))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(exported.fileName).build().toString(),
            )
            .body(exported.resource)
    }
}
