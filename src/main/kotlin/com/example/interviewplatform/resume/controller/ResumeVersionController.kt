package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.ActivateResumeVersionResponse
import com.example.interviewplatform.resume.dto.ResumeExperienceSnapshotResponseDto
import com.example.interviewplatform.resume.dto.ResumeRiskItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeSkillSnapshotResponseDto
import com.example.interviewplatform.resume.service.ResumeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
    @GetMapping("/{versionId}/skills")
    @Operation(summary = "Get extracted skills for resume version")
    fun getResumeVersionSkills(@PathVariable versionId: Long): ResumeSkillSnapshotResponseDto =
        resumeService.listResumeVersionSkills(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/experiences")
    @Operation(summary = "Get extracted experiences for resume version")
    fun getResumeVersionExperiences(@PathVariable versionId: Long): ResumeExperienceSnapshotResponseDto =
        resumeService.listResumeVersionExperiences(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/risks")
    @Operation(summary = "Get extracted risk items for resume version")
    fun getResumeVersionRisks(@PathVariable versionId: Long): ResumeRiskItemResponseDto =
        resumeService.listResumeVersionRisks(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/file")
    @Operation(summary = "Download resume version file")
    fun downloadResumeVersionFile(@PathVariable versionId: Long): ResponseEntity<Resource> {
        val file = resumeService.downloadResumeVersionFile(currentUserProvider.currentUserId(), versionId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.fileName}\"")
            .header(HttpHeaders.CONTENT_TYPE, file.contentType)
            .body(file.resource)
    }

    @PostMapping("/{versionId}/activate")
    @Operation(summary = "Activate resume version")
    fun activate(@PathVariable versionId: Long): ActivateResumeVersionResponse =
        resumeService.activateResumeVersion(currentUserProvider.currentUserId(), versionId)
}
