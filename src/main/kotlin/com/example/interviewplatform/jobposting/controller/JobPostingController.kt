package com.example.interviewplatform.jobposting.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.jobposting.dto.CreateJobPostingRequest
import com.example.interviewplatform.jobposting.dto.JobPostingDto
import com.example.interviewplatform.jobposting.service.JobPostingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Job Posting")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/job-postings")
class JobPostingController(
    private val jobPostingService: JobPostingService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "List saved job postings")
    fun listJobPostings(): List<JobPostingDto> =
        jobPostingService.listJobPostings(currentUserProvider.currentUserId())

    @PostMapping
    @Operation(summary = "Create and parse one job posting")
    fun createJobPosting(@Valid @RequestBody request: CreateJobPostingRequest): JobPostingDto =
        jobPostingService.createJobPosting(currentUserProvider.currentUserId(), request)

    @GetMapping("/{jobPostingId}")
    @Operation(summary = "Get one saved job posting")
    fun getJobPosting(@PathVariable jobPostingId: Long): JobPostingDto =
        jobPostingService.getJobPosting(currentUserProvider.currentUserId(), jobPostingId)
}
