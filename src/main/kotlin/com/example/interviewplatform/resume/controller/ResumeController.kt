package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.CreateResumeRequest
import com.example.interviewplatform.resume.dto.CreateResumeVersionRequest
import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.dto.ResumeVersionDto
import com.example.interviewplatform.resume.service.ResumeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Resume")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resumes")
class ResumeController(
    private val resumeService: ResumeService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "List current user resumes")
    fun listResumes(): List<ResumeDto> = resumeService.listUserResumes(currentUserProvider.currentUserId())

    @GetMapping("/latest")
    @Operation(summary = "Get latest or primary resume")
    fun getLatestResume(): ResumeDto = resumeService.getLatestResume(currentUserProvider.currentUserId())

    @PostMapping
    @Operation(summary = "Create resume container")
    fun createResume(@Valid @RequestBody request: CreateResumeRequest): ResumeDto =
        resumeService.createResume(currentUserProvider.currentUserId(), request)

    @PostMapping("/{resumeId}/versions")
    @Operation(summary = "Create resume version")
    fun createResumeVersion(
        @PathVariable resumeId: Long,
        @Valid @RequestBody request: CreateResumeVersionRequest,
    ): ResumeVersionDto = resumeService.createResumeVersion(currentUserProvider.currentUserId(), resumeId, request)

    @PostMapping("/{resumeId}/versions/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload PDF resume version")
    fun uploadResumeVersion(
        @PathVariable resumeId: Long,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("summaryText", required = false) summaryText: String?,
    ): ResumeVersionDto = resumeService.uploadResumeVersion(currentUserProvider.currentUserId(), resumeId, file, summaryText)
}
