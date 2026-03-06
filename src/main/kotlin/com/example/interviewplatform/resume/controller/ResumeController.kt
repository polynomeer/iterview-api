package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.service.ResumeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/resumes")
class ResumeController(
    private val resumeService: ResumeService,
) {
    @GetMapping
    fun listResumes(): List<ResumeDto> = resumeService.listUserResumes(DEFAULT_USER_ID)

    private companion object {
        const val DEFAULT_USER_ID = 1L
    }
}
