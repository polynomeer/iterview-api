package com.example.interviewplatform.answer.controller

import com.example.interviewplatform.answer.dto.AnswerAttemptDetailResponseDto
import com.example.interviewplatform.answer.dto.AnswerAttemptListItemDto
import com.example.interviewplatform.answer.dto.AnswerAnalysisDto
import com.example.interviewplatform.answer.dto.SubmitAnswerRequest
import com.example.interviewplatform.answer.dto.SubmitAnswerResponseDto
import com.example.interviewplatform.answer.service.AnswerService
import com.example.interviewplatform.common.service.CurrentUserProvider
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
@Tag(name = "Answer")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
class AnswerController(
    private val answerService: AnswerService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/questions/{questionId}/answers")
    @Operation(summary = "Submit answer attempt")
    fun submitAnswer(
        @PathVariable questionId: Long,
        @Valid @RequestBody request: SubmitAnswerRequest,
    ): SubmitAnswerResponseDto = answerService.submitAnswer(currentUserProvider.currentUserId(), questionId, request)

    @GetMapping("/questions/{questionId}/answers")
    @Operation(summary = "List answer attempts for question")
    fun listAnswers(@PathVariable questionId: Long): List<AnswerAttemptListItemDto> =
        answerService.listQuestionAnswers(currentUserProvider.currentUserId(), questionId)

    @GetMapping("/answer-attempts/{answerAttemptId}")
    @Operation(summary = "Get answer attempt detail")
    fun getAnswerAttempt(@PathVariable answerAttemptId: Long): AnswerAttemptDetailResponseDto =
        answerService.getAnswerAttempt(currentUserProvider.currentUserId(), answerAttemptId)

    @GetMapping("/answer-attempts/{answerAttemptId}/analysis")
    @Operation(summary = "Get answer analysis")
    fun getAnswerAnalysis(@PathVariable answerAttemptId: Long): AnswerAnalysisDto =
        answerService.getAnswerAnalysis(currentUserProvider.currentUserId(), answerAttemptId)
}
