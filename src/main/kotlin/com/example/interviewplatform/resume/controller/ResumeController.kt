package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.CreateResumeRequest
import com.example.interviewplatform.resume.dto.CreateResumeVersionRequest
import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.dto.ResumeVersionDto
import com.example.interviewplatform.resume.service.ResumeService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/resumes")
class ResumeController(
    private val resumeService: ResumeService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    fun listResumes(): List<ResumeDto> = resumeService.listUserResumes(currentUserProvider.currentUserId())

    @PostMapping
    fun createResume(@Valid @RequestBody request: CreateResumeRequest): ResumeDto =
        resumeService.createResume(currentUserProvider.currentUserId(), request)

    @PostMapping("/{resumeId}/versions")
    fun createResumeVersion(
        @PathVariable resumeId: Long,
        @Valid @RequestBody request: CreateResumeVersionRequest,
    ): ResumeVersionDto = resumeService.createResumeVersion(currentUserProvider.currentUserId(), resumeId, request)
}
