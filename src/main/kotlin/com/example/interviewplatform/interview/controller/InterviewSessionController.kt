package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.interview.dto.CreateInterviewSessionRequest
import com.example.interviewplatform.interview.dto.InterviewSessionAdvanceResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionAnswerResponseDto
import com.example.interviewplatform.interview.dto.InterviewSessionDetailResponseDto
import com.example.interviewplatform.interview.dto.SubmitInterviewSessionAnswerRequest
import com.example.interviewplatform.interview.service.InterviewSessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@Tag(name = "Interview Session")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/interview-sessions")
class InterviewSessionController(
    private val interviewSessionService: InterviewSessionService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping
    @Operation(summary = "Create interview session")
    fun createSession(@Valid @RequestBody request: CreateInterviewSessionRequest): InterviewSessionDetailResponseDto =
        interviewSessionService.createSession(currentUserProvider.currentUserId(), request)

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get interview session detail")
    fun getSession(@PathVariable sessionId: Long): InterviewSessionDetailResponseDto =
        interviewSessionService.getSession(currentUserProvider.currentUserId(), sessionId)

    @PostMapping("/{sessionId}/answers")
    @Operation(summary = "Submit answer for interview session question")
    fun submitAnswer(
        @PathVariable sessionId: Long,
        @Valid @RequestBody request: SubmitInterviewSessionAnswerRequest,
    ): InterviewSessionAnswerResponseDto =
        interviewSessionService.submitAnswer(currentUserProvider.currentUserId(), sessionId, request)

    @PostMapping("/{sessionId}/next-question")
    @Operation(summary = "Advance interview session to next question")
    fun nextQuestion(@PathVariable sessionId: Long): InterviewSessionAdvanceResponseDto =
        interviewSessionService.nextQuestion(currentUserProvider.currentUserId(), sessionId)
}
